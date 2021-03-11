package com.fortify.sync.fod_ssc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fortify.client.fod.api.FoDReleaseAPI;
import com.fortify.client.fod.connection.FoDAuthenticatingRestConnection;
import com.fortify.client.fod.connection.FoDAuthenticatingRestConnection.FoDAuthenticatingRestConnectionBuilder;
import com.fortify.client.ssc.api.SSCAttributeDefinitionAPI;
import com.fortify.client.ssc.api.SSCAttributeDefinitionAPI.SSCAttributeDefinitionHelper;
import com.fortify.client.ssc.api.SSCIssueTemplateAPI;
import com.fortify.client.ssc.api.SSCIssueTemplateAPI.SSCIssueTemplateHelper;
import com.fortify.client.ssc.connection.SSCAuthenticatingRestConnection;
import com.fortify.client.ssc.connection.SSCAuthenticatingRestConnection.SSCAuthenticatingRestConnectionBuilder;
import com.fortify.sync.fod_ssc.config.LinkReleasesTaskConfig;
import com.fortify.sync.fod_ssc.config.SyncScansTaskConfig;
import com.fortify.sync.fod_ssc.connection.ssc.api.SSCSyncAttr;

/**
 * This {@link SpringBootApplication} class provides the following functionality:
 * <ul>
 *  <li><code>main()</code> method for starting the utility:
 *      <ul>
 *       <li>Set various system properties by calling {@link Constants#updateSystemProperties()}</li>
 *       <li>Check whether the required configuration file exists</li>
 *       <li>Start the actual application by calling {@link SpringApplication#run(Class, String...)}</li>
 *      </ul></li>
 *  <li>{@link Bean}-annotated methods that provide configuration beans and
 *      connections instances for accessing FoD and SSC<li>
 * </ul>
 * @author Ruud Senden
 *
 */
@SpringBootApplication
@EnableScheduling
public class FortifySyncFoDToSSCApplication {
	private static final Logger LOG = LoggerFactory.getLogger(FortifySyncFoDToSSCApplication.class);
	
	/**
	 * Start the application
	 * @param args
	 */
	public static void main(String[] args) {
		Constants.updateSystemProperties();
		SpringApplication.run(FortifySyncFoDToSSCApplication.class, args);
	}
	
	/**
	 * Get a {@link FoDAuthenticatingRestConnectionBuilder} instance, automatically
	 * wiring all FoD connection properties defined in the configuration file. 
	 * @return
	 */
	@Bean
	@ConfigurationProperties("sync.connections.fod") 
	public FoDAuthenticatingRestConnectionBuilder fodConnectionBuilder() {
		return FoDAuthenticatingRestConnection.builder().multiThreaded(true).scopes("view-apps", "view-issues");
	}
	
	/**
	 * Get a {@link SSCAuthenticatingRestConnectionBuilder} instance, automatically
	 * wiring all SSC connection properties defined in the configuration file. 
	 * @return
	 */
	@Bean
	@ConfigurationProperties("sync.connections.ssc") 
	public SSCAuthenticatingRestConnectionBuilder sscConnectionBuilder() {
		return SSCAuthenticatingRestConnection.builder().multiThreaded(true);
	}
	
	/**
	 * Get a {@link LinkReleasesTaskConfig} instance, automatically wiring all 
	 * configuration properties defined in the configuration file. 
	 * @return
	 */
	@Bean
	@ConfigurationProperties("sync.tasks.link-releases")
	public LinkReleasesTaskConfig configLinkReleasesTask() {
		return new LinkReleasesTaskConfig();
	}
	
	/**
	 * Get a {@link SyncScansTaskConfig} instance, automatically wiring all 
	 * configuration properties defined in the configuration file. 
	 * @return
	 */
	@Bean
	@ConfigurationProperties("sync.tasks.sync-scans")
	public SyncScansTaskConfig configSyncScansTask() {
		return new SyncScansTaskConfig();
	}
	
	/**
	 * Instantiate and test the {@link FoDAuthenticatingRestConnection} 
	 * instance used to connect to FoD, based on the connection builder 
	 * returned by {@link #fodConnectionBuilder()}. 
	 * @return
	 */
	@Bean
	public FoDAuthenticatingRestConnection fodConnection() {
		return testFoDConnection(fodConnectionBuilder().build());
	}
	
	/**
	 * Test whether we can successfully connect to FoD by executing a 
	 * simple, lightweight query.
	 * 
	 * @param conn
	 */
	public final FoDAuthenticatingRestConnection testFoDConnection(FoDAuthenticatingRestConnection conn) {
		LOG.info("Testing whether FoD can be contacted");
		conn.api(FoDReleaseAPI.class)
			.queryReleases()
			.maxResults(1)
			.paramFields(false,"releaseId")
			.build().getUnique();
		return conn;
	}
	
	/**
	 * Instantiate and test the {@link SSCAuthenticatingRestConnection} 
	 * instance used to connect to SSC, based on the connection builder 
	 * returned by {@link #sscConnectionBuilder()}. 
	 * @return
	 */
	@Bean
	public SSCAuthenticatingRestConnection sscConnection() {
		return testSSCConnection(sscConnectionBuilder().build());
	}
	
	/**
	 * Test whether the necessary application attributes have been defined on SSC.
	 * This also implicitly tests whether we can successfully connect to SSC.
	 * 
	 * @param conn
	 */
	public final SSCAuthenticatingRestConnection testSSCConnection(SSCAuthenticatingRestConnection conn) {
		LOG.info("Testing whether SSC can be contacted, and all required application attributes have been defined");
		SSCSyncAttr.checkAndCreateSSCAttributeDefinitions(conn);
		return conn;
	}
	
	@Bean 
	public SSCAttributeDefinitionHelper sscAttributeDefinitionHelper() {
		return sscConnection().api(SSCAttributeDefinitionAPI.class).getAttributeDefinitionHelper();
	}
	
	@Bean 
	public SSCIssueTemplateHelper sscIssueTemplateHelper() {
		return sscConnection().api(SSCIssueTemplateAPI.class).getIssueTemplateHelper();
	}
}
