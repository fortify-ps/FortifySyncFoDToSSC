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
package com.fortify.sync.fod_ssc.config;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fortify.sync.fod_ssc.FortifySyncFoDToSSCApplication;
import com.fortify.sync.fod_ssc.connection.ssc.api.SSCSyncAttr;
import com.fortify.sync.fod_ssc.task.LinkReleasesTask;
import com.fortify.util.spring.expression.SimpleExpression;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * This {@link Data} class holds the configuration for {@link LinkReleasesTask}.
 * This configuration is automatically loaded from the configuration file by
 * {@link FortifySyncFoDToSSCApplication#configLinkReleasesTask()}.
 *  
 * @author Ruud Senden
 *
 */
@Data
public class LinkReleasesTaskConfig implements IScheduleConfig {
	private String cronSchedule = "-";
	private ConfigJobLinkReleasesFoD fod = new ConfigJobLinkReleasesFoD();
	private ConfigJobLinkReleasesSSC ssc = new ConfigJobLinkReleasesSSC();
	
    @Data public static class ConfigJobLinkReleasesFoD {
    	private ConfigFoDQuery filters = new ConfigFoDQuery(); 
    }
    
    @Data public static class ConfigJobLinkReleasesSSC {
    	private boolean linkOnlyIfSyncableScans = true;
    	private ConfigAutoCreate autoCreateVersions = new ConfigAutoCreate();
    	private String[] enabledFoDScanTypes = SSCSyncAttr.INCLUDE_FOD_SCAN_TYPES.getAttributeOptionNames();
    	private final MultiValueMap<String, String> attributeExpressions = new LinkedMultiValueMap<>();
    	private String applicationDescriptionExpression = "${application?.applicationDescription}";
    	private String versionDescriptionExpression = "${releaseDescription}";
    }
    
    @Data public static class ConfigFoDQuery {
    	private ConfigApplicationFilters application = new ConfigApplicationFilters();
    	private ConfigReleaseFilters release = new ConfigReleaseFilters();
    } 
    
    @Data public static abstract class AbstractFoDQueryConfig {
    	private String fodFilterParam;
    	private SimpleExpression[] filterExpressions;
    }
    
    @Data @EqualsAndHashCode(callSuper=true) public static class ConfigApplicationFilters extends AbstractFoDQueryConfig {} 
    
    @Data @EqualsAndHashCode(callSuper=true) public static class ConfigReleaseFilters extends AbstractFoDQueryConfig {
    	public ConfigReleaseFilters() {
    		setFodFilterParam("sdlcStatusType:Production|QA|Development");
    	}
    } 

    @Data public static class ConfigAutoCreate {
    	private boolean enabled = true;
    	private String issueTemplateName = "Prioritized High Risk Issue Template";
	}
}