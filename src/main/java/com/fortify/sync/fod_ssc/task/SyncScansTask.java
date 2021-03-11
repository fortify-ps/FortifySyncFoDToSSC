/*******************************************************************************
 * (c) Copyright 2020 Micro Focus or one of its affiliates
 *
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the 
 * "Software"), to deal in the Software without restriction, including without 
 * limitation the rights to use, copy, modify, merge, publish, distribute, 
 * sublicense, and/or sell copies of the Software, and to permit persons to 
 * whom the Software is furnished to do so, subject to the following 
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be included 
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY 
 * KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE 
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR 
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, 
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN 
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS 
 * IN THE SOFTWARE.
 ******************************************************************************/
package com.fortify.sync.fod_ssc.task;

import java.io.File;
import java.io.FilenameFilter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.Date;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import com.fortify.client.fod.api.FoDReleaseAPI;
import com.fortify.client.fod.connection.FoDAuthenticatingRestConnection;
import com.fortify.client.ssc.api.SSCApplicationVersionAttributeAPI;
import com.fortify.client.ssc.api.SSCArtifactAPI;
import com.fortify.client.ssc.api.SSCAttributeDefinitionAPI.SSCAttributeDefinitionHelper;
import com.fortify.client.ssc.connection.SSCAuthenticatingRestConnection;
import com.fortify.sync.fod_ssc.config.SyncScansTaskConfig;
import com.fortify.sync.fod_ssc.connection.ssc.api.SyncAPI;
import com.fortify.sync.fod_ssc.connection.ssc.api.SyncAPI.SyncConfigPredicate;
import com.fortify.sync.fod_ssc.connection.ssc.api.SyncConfig;
import com.fortify.sync.fod_ssc.connection.ssc.api.SyncData;
import com.fortify.sync.fod_ssc.connection.ssc.api.SyncStatus;
import com.fortify.util.rest.json.JSONMap;

/**
 * This task is responsible for downloading scans from FoD and uploading them to SSC.
 * The schedule for running this task is configured using {@link SyncScansTaskConfig}.
 * All other relevant configuration is stored as SSC application version attributes:
 * <ul>
 *  <li><code>{@value SyncConfig#SSC_ATTR_FOD_RELEASE_ID}</code>: 
 *      The FoD release id with which the current SSC application version should be synchronized</li>
 *  <li><code>{@value SyncConfig#SSC_ATTR_INCLUDE_FOD_SCAN_TYPES}</code>: 
 *      The scan types to be synchronized (Static/Dynamic, and Mobile once supported by FoD)</li>
 *  <li><code>{@value SyncStatus#FOD_SYNC_STATUS}</code>: 
 *      The current synchronization status, as stored during the last sync</li>
 * </ul> 
 * 
 * @author Ruud Senden
 *
 */
@Component
public class SyncScansTask extends AbstractScheduledTask<SyncScansTaskConfig> implements IHasSyncableScanChecker {
	private static final String PFX_SCAN_FILE_NAME = "FoDScan-";
	private static final Logger LOG = LoggerFactory.getLogger(SyncScansTask.class);
	private static final SimpleDateFormat FMT_FOD_DATE = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	private static final FastDateFormat FMT_TIMESTAMP = FastDateFormat.getInstance("yyyyMMdd-HHmmss.SSS");
	@Autowired private SyncScansTaskConfig config;
	@Autowired private FoDAuthenticatingRestConnection fodConn;
	@Autowired private SSCAuthenticatingRestConnection sscConn;
	@Autowired private SSCAttributeDefinitionHelper attributeDefinitionHelper;

	/**
	 * Allow our superclass to access our configuration
	 */
	@Override
	protected SyncScansTaskConfig getConfig() {
		return config;
	}
	
	@PostConstruct
	public void createScansTempDir() {
		File scansTempDir = new File(config.getScansTempDir());
		LOG.info("Creating directory {} for temporary scan downloads", scansTempDir.getAbsolutePath());
		scansTempDir.mkdirs();
	}

	/**
	 * This method is called by our superclass based on the configured schedule. Based on the functionality
	 * provided by {@link SyncAPI}, this method will call the {@link #processSyncedApplicationVersion(SyncData)}
	 * method for every SSC Application version for which sync is enabled. 
	 */
	protected void runTask() {
		try {
			sscConn.api(SyncAPI.class).processSyncData(attributeDefinitionHelper, this::processSyncedApplicationVersion, SyncConfigPredicate.IS_SYNC_ENABLED);
		} finally {
			deleteOldScans();
		}
	}
	
	/**
	 * Delete scan files older than the configured number of minutes.
	 */
	private final void deleteOldScans() {
		String[] filesToDelete = new File(config.getScansTempDir()).list(scansToBeDeletedFilter);
		for ( String fileToDelete : filesToDelete ) {
			new File(config.getScansTempDir(), fileToDelete).delete();
		}
	}
	
	/**
	 * Anonymous {@link FilenameFilter} instance
	 */
	private final FilenameFilter scansToBeDeletedFilter = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String name) {
			return name.startsWith(PFX_SCAN_FILE_NAME) &&
					new File(dir, name).lastModified() < System.currentTimeMillis() - config.getDeleteScansOlderThanMinutes()*1000*60;
		}
	};

	/**
	 * Invoke the {@link #processSyncedApplicationVersion(SyncConfig, SyncStatus)} method with
	 * the appropriate {@link SyncConfig} and {@link SyncStatus}, retrieved from the given 
	 * {@link SyncData}.
	 * 
	 * @param syncData
	 */
	private final void processSyncedApplicationVersion(SyncData syncData) {
		String sscApplicationVersionId = syncData.getSSCApplicationVersionId();
		SyncConfig syncConfig = syncData.getSyncConfig();
		SyncStatus syncStatus = syncData.getSyncStatus().newIfDifferentFoDReleaseId(syncConfig.getFodReleaseId());
		processSyncedApplicationVersion(sscApplicationVersionId, syncConfig, syncStatus);
	}

	/**
	 * If there are any scan types to be synchronized according to the given {@link SyncConfig}, 
	 * this method will retrieve the FoD release to be synchronized, and then call the 
	 * {@link #syncScanTypeIfNecessary(String, JSONMap, SyncStatus, String)} for each scan
	 * type.
	 * 
	 * @param syncConfig
	 * @param syncStatus
	 */
	private final void processSyncedApplicationVersion(String sscApplicationVersionId, SyncConfig syncConfig, SyncStatus syncStatus) {
		String[] scanTypes = syncConfig.getIncludedScanTypes();
		if ( scanTypes!=null && scanTypes.length > 0 ) {
			String fodReleaseId = syncConfig.getFodReleaseId();
			JSONMap fodRelease = getFodRelease(fodReleaseId);
			if ( fodRelease==null ) {
				LOG.warn("FoD release id {} does not exist; skipping sync for application version id {}", fodReleaseId, sscApplicationVersionId);
			} else {
				processSyncedApplicationVersion(sscApplicationVersionId, syncStatus, scanTypes, fodRelease);
			}
		}
		updateApplicationVersion(sscApplicationVersionId, syncStatus);
	}
	
	private final void updateApplicationVersion(String sscApplicationVersionId, SyncStatus syncStatus) {
		if ( syncStatus.isModified() ) {
			LOG.debug("Updating sync status for application version id {}", sscApplicationVersionId);
			MultiValueMap<String, Object> attributes = syncStatus.asAttributesMap();
			sscConn.api(SSCApplicationVersionAttributeAPI.class).updateApplicationVersionAttributes(sscApplicationVersionId)
				.withAttributeDefinitionHelper(attributeDefinitionHelper)
				.byNameOrId(attributes)
				.execute();
		}
	}

	private void processSyncedApplicationVersion(String sscApplicationVersionId, SyncStatus syncStatus, String[] scanTypes, JSONMap fodRelease) {
		for ( String scanType : scanTypes ) {
			try {
				syncScanTypeIfNecessary(sscApplicationVersionId, fodRelease, syncStatus, scanType);
			} catch (RuntimeException e) {
				// We catch the exception here in order to allow other scan types to be processed,
				// and scan status to be updated for successfully processed scan types.
				LOG.error("Error processing scan type "+scanType,e);
			} 
		}
	}

	/**
	 * Compare the current FoD scan date for the given scan type with the current sync status. If
	 * the given scan type was not uploaded to SSC before, or current FoD scan date is after the
	 * scan date of the previously uploaded scan, the {@link #syncScanType(String, JSONMap, String)}
	 * method will be called to download latest scan results from FoD and upload to SSC, and the
	 * current sync status will be updated with the current FoD scan date. 
	 *  
	 * @param sscApplicationVersionId
	 * @param fodRelease
	 * @param syncStatus
	 * @param scanType
	 */
	private final void syncScanTypeIfNecessary(String sscApplicationVersionId, JSONMap fodRelease, SyncStatus syncStatus, String scanType) {
		Date fodScanDate = getFoDScanDate(fodRelease, scanType);
		Date oldScanDate = syncStatus.getScanDate(scanType);
		LOG.debug("[{} - {}] Scan type {}: current scan date {}, previous scan date {}", fodRelease.get("applicationName", String.class), fodRelease.get("releaseName", String.class), scanType, fodScanDate, oldScanDate);
		if ( isSyncableScanDate(fodScanDate) && (oldScanDate==null || fodScanDate.after(oldScanDate)) ) {
			syncScanType(sscApplicationVersionId, fodRelease, scanType);
			syncStatus.setScanDate(scanType, fodScanDate);
		}
	}
	
	
	/* (non-Javadoc)
	 * @see com.fortify.sync.fod_ssc.task.IHasSyncableScan#hasSyncableScan(com.fortify.util.rest.json.JSONMap, java.lang.String)
	 */
	@Override
	public final boolean hasSyncableScan(JSONMap fodRelease, String scanType) {
		Date fodScanDate = getFoDScanDate(fodRelease, scanType);
		return isSyncableScanDate(fodScanDate);
	}

	/**
	 * Check whether the given FoD scan date is syncable, i.e. not null and not older
	 * than configured number of days.
	 * @param fodScanDate
	 * @return
	 */
	private final boolean isSyncableScanDate(Date fodScanDate) {
		if ( fodScanDate == null ) { return false; }
		ZonedDateTime now = ZonedDateTime.now();
		ZonedDateTime oldestAllowedScanDate = now.plusDays(-config.getIgnoreScansOlderThanDays());
		return fodScanDate.toInstant().isAfter(oldestAllowedScanDate.toInstant());
	}

	/**
	 * Download the given scan type from the given FoD release, and upload the scan to the given
	 * SSC application version.
	 * 
	 * @param sscApplicationVersionId
	 * @param fodRelease
	 * @param scanType
	 */
	private final void syncScanType(String sscApplicationVersionId, JSONMap fodRelease, String scanType) {
		Path scanFile = Paths.get(config.getScansTempDir(), getScanTempFileName(fodRelease, scanType));
		// TODO Pipe FPR input stream from FoD directly to SSC, instead of using temp file
		String fodReleaseId = fodRelease.get("releaseId",String.class);
		LOG.info("Downloading {} scan from FoD release id {}", scanType, fodReleaseId);
		fodConn.api(FoDReleaseAPI.class).saveFPR(fodReleaseId, scanType, scanFile);
		LOG.info("Uploading {} scan to SSC application version id {}", scanType, sscApplicationVersionId);
		sscConn.api(SSCArtifactAPI.class).uploadArtifact(sscApplicationVersionId, scanFile.toFile());
	}

	private String getScanTempFileName(JSONMap fodRelease, String scanType) {
		try {
			String fileName = String.format("%s%s-%s-%s-%s.fpr", 
					PFX_SCAN_FILE_NAME,
					fodRelease.get("applicationName", String.class), 
					fodRelease.get("releaseName", String.class), 
					scanType, FMT_TIMESTAMP.format(new Date()));
			return URLEncoder.encode(fileName, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Error generating temporary file name", e);
		}
	}

	/**
	 * Get the FoD release JSON object for the given FoD release id. The returned {@link JSONMap} will
	 * contain the FoD release id, application name, release name, and static/dynamic/mobile scan dates.
	 * @param fodReleaseId
	 * @return
	 */
	private final JSONMap getFodRelease(String fodReleaseId) {
		return fodConn.api(FoDReleaseAPI.class)
				.queryReleases()
				.releaseId(false, fodReleaseId)
				.paramFields(false, "releaseId", "applicationName", "releaseName", "staticScanDate", "dynamicScanDate", "mobileScanDate")
				.build().getUnique();
	}
	
	/**
	 * Get and parse the [scanType]ScanDate property from the given release JSON object.
	 * 
	 * @param fodRelease
	 * @param scanType
	 * @return
	 */
	private static final Date getFoDScanDate(JSONMap fodRelease, String scanType) {
		return parseFoDDate(fodRelease.get(scanType.toLowerCase()+"ScanDate", String.class));
	}
	
	/**
	 * Parse an FoD scan date according to the format defined by {@value #FMT_FOD_DATE}
	 * @param dateString
	 * @return
	 */
	private static final Date parseFoDDate(String dateString) {
		if ( dateString == null ) { return null; }
		try {
			synchronized (FMT_FOD_DATE) { // Avoid potential Format.parse()-related race conditions
				return FMT_FOD_DATE.parse(StringUtils.substringBefore(dateString, "."));
			}
		} catch ( ParseException e ) {
			throw new RuntimeException("Error parsing scan date "+dateString+" returned by FoD", e);
		}
	}
}
