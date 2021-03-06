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

import com.fortify.sync.fod_ssc.Constants;
import com.fortify.sync.fod_ssc.FortifySyncFoDToSSCApplication;
import com.fortify.sync.fod_ssc.task.SyncScansTask;

import lombok.Data;

/**
 * This {@link Data} class holds the configuration for {@link SyncScansTask}.
 * This configuration is automatically loaded from the configuration file by
 * {@link FortifySyncFoDToSSCApplication#configSyncScansTask()}.
 *  
 * @author Ruud Senden
 *
 */
@Data
public class SyncScansTaskConfig implements IScheduleConfig {
	private String cronSchedule = "-";
	private long deleteScansOlderThanMinutes = 0;
	private long ignoreScansOlderThanDays = 730; // Default FoD retention policy is 2 years
	private String scansTempDir = Constants.SYNC_HOME+"/scans";
}