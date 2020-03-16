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
package com.fortify.sync.fod_ssc.connection.ssc.api;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortify.client.ssc.api.SSCApplicationVersionAttributeAPI;
import com.fortify.client.ssc.connection.SSCAuthenticatingRestConnection;
import com.fortify.util.rest.json.JSONMap;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * This class holds the current sync status for an SSC application version. This includes the 
 * following information:
 * <ul>
 *  <li>The FoD release id with which this application version was last synced</li>
 *  <li>For each of the scan types being synced, the scan date for the scan that was last synced</li>
 *  <li>A flag indicating whether the scan status has been changed during the current run</li>
 * </ul>
 * Apart from the actual sync status data, this class provides various methods for storing
 * and loading the current sync status as an SSC application version attribute.  
 *  
 * @author Ruud Senden
 *
 */
@Data
public final class SyncStatus {
	private static final String SSC_ATTR_FOD_SYNC_STATUS = SSCSyncAttr.FOD_SYNC_STATUS.getAttributeName();
	private static final Logger LOG = LoggerFactory.getLogger(SyncAPI.class);
	private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	
	@JsonProperty private String fodReleaseId;
	@Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE) 
	@JsonProperty private Map<String,Date> scanDates = new HashMap<>();
	@JsonIgnore private boolean modified;
	
	public static final SyncStatus getFromApplicationVersion(JSONMap sscApplicationVersion) {
		JSONMap attributeValuesByName = sscApplicationVersion.get("attributeValuesByName", JSONMap.class);
		return parse(attributeValuesByName.get(SSC_ATTR_FOD_SYNC_STATUS, String.class));
	}
	
	public final void updateApplicationVersion(SSCAuthenticatingRestConnection sscConn, String sscApplicationVersionId) {
		if ( isModified() ) {
			LOG.debug("Updating sync status for application version id {}", sscApplicationVersionId);
			MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
			attributes.add(SSC_ATTR_FOD_SYNC_STATUS, asSyncStatusString());
			sscConn.api(SSCApplicationVersionAttributeAPI.class)
				.updateApplicationVersionAttributes(sscApplicationVersionId, attributes);
		}
	}
	
	/**
	 * Parse a sync status string that was previously generated by {@link #asSyncStatusString()}.
	 * 
	 * @param syncStatusString
	 * @return
	 */
	private static final SyncStatus parse(String syncStatusString) {
		SyncStatus result = new SyncStatus();
		try {
			if ( StringUtils.isNotBlank(syncStatusString) ) {
				result = MAPPER.readerForUpdating(result).readValue(syncStatusString);
				result.modified = false;
			}
		} catch (JsonProcessingException e) {
			LOG.warn("Sync Status cannot be parsed; FPR files will be re-synced");
		}
		return result;
	}

	/**
	 * Return a string representation of the current sync status, allowing to store
	 * the current sync status for later parsing by the {@link #parse(String)} method.
	 * @return
	 */
	private final String asSyncStatusString() {
		try {
			return MAPPER.writeValueAsString(this);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Exception generating FoD sync status string", e);
		}
	}
	
	/**
	 * If the given FoD release id is the same as our current FoD release id,
	 * this method returns the current {@link SyncStatus} instance. If the 
	 * FoD release id's differ, a new (fresh) {@link SyncStatus} instance is
	 * returned.
	 * 
	 * @param fodReleaseId
	 * @return
	 */
	public final SyncStatus newIfDifferentFoDReleaseId(String fodReleaseId) {
		if ( Objects.equals(this.fodReleaseId, fodReleaseId) ) {
			return this;
		} else {
			if ( this.fodReleaseId!=null ) {
				LOG.warn("Linked FoD Release Id has changed since last sync, ignoring previous scan status");
			}
			SyncStatus result = new SyncStatus();
			result.setFoDReleaseId(fodReleaseId);
			return result;
		}
	}
	
	/**
	 * Update the FoD release id. If the given release id is equal to the currently 
	 * stored release id, this method has no effect. If the release id's differ, 
	 * the new release id is stored and the modified flag is set to true.
	 *  
	 * @param fodReleaseId
	 */
	public final void setFoDReleaseId(String fodReleaseId) {
		if ( !Objects.equals(this.fodReleaseId, fodReleaseId) ) {
			this.modified = true;
			this.fodReleaseId = fodReleaseId;
		}
	}

	/**
	 * Get the scan date for the last synced scan of the given scan type. If 
	 * the given scan type has not been synced before, this method returns null.
	 * @param scanType
	 * @return
	 */
	public final Date getScanDate(String scanType) {
		return this.scanDates.get(scanType.toLowerCase());
	}

	/**
	 * Update the scan date for the given scan type. If the given scan date is 
	 * equal to the currently stored scan date, this method has no effect. If 
	 * the scan dates differ, the new scan date is stored and the modified flag 
	 * is set to true.
	 * 
	 * @param scanType
	 * @param scanDate
	 */
	public void setScanDate(String scanType, Date scanDate) {
		if ( !Objects.equals(getScanDate(scanType), scanDate)) {
			this.modified = true;
			this.scanDates.put(scanType.toLowerCase(), scanDate);
		}
	}
}