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

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fortify.client.ssc.api.SSCAttributeDefinitionAPI;
import com.fortify.client.ssc.api.SSCAttributeDefinitionAPI.SSCAttributeDefinitionCategory;
import com.fortify.client.ssc.api.SSCAttributeDefinitionAPI.SSCAttributeDefinitionType;
import com.fortify.client.ssc.api.SSCAttributeDefinitionAPI.SSCCreateAttributeDefinitionBuilder;
import com.fortify.client.ssc.api.SSCAttributeDefinitionAPI.SSCCreateAttributeDefinitionBuilder.SSCAttributeDefinitionOption;
import com.fortify.client.ssc.connection.SSCAuthenticatingRestConnection;
import com.fortify.util.rest.json.JSONMap;

public enum SSCSyncAttr {
	INCLUDE_FOD_SCAN_TYPES("FoD Sync - Include Scan Types", SSCAttributeDefinitionType.MULTIPLE, false, "Static", "Dynamic", "Mobile"),
	FOD_RELEASE_ID("FoD Sync - Release Id", SSCAttributeDefinitionType.INTEGER, false),
	// TODO FOD_SYNC_STATUS currently visible due to SSC Defect 324058, should be hidden once SSC is fixed 
	FOD_SYNC_STATUS("FoD Sync - Status", SSCAttributeDefinitionType.LONG_TEXT, false); 
	
	private static final Logger LOG = LoggerFactory.getLogger(SSCSyncAttr.class);
	private final String attributeName;
	private final SSCAttributeDefinitionType attributeType;
	private final boolean hidden;
	private final String[] attributeOptionNames;
	SSCSyncAttr(String attributeName, SSCAttributeDefinitionType attributeType, boolean hidden, String... attributeOptionNames) {
		this.attributeName = attributeName;
		this.attributeType = attributeType;
		this.hidden = hidden;
		this.attributeOptionNames = attributeOptionNames;
	}
	
	public String getAttributeName() {
		return attributeName;
	}
	
	public SSCAttributeDefinitionType getAttributeType() {
		return attributeType;
	}
	
	public String[] getAttributeOptionNames() {
		return attributeOptionNames;
	}
	
	public boolean isHidden() {
		return hidden;
	}
	
	public JSONMap createAttributeDefinition(SSCAuthenticatingRestConnection conn) {
		SSCCreateAttributeDefinitionBuilder builder = conn.api(SSCAttributeDefinitionAPI.class).createAttributeDefinition()
			.name(getAttributeName())
			.description("Created by FortifySyncFoDToSSC")
			.category(SSCAttributeDefinitionCategory.TECHNICAL)
			.hidden(isHidden())
			.type(getAttributeType());
		if ( attributeOptionNames!=null ) {
			for ( String attributeOptionName : attributeOptionNames ) {
				builder.option(new SSCAttributeDefinitionOption(attributeOptionName));
			}
		}
		return builder.execute();
	}
	
	public static final void checkAndCreateSSCAttributeDefinitions(SSCAuthenticatingRestConnection conn) {
		Set<String> missingAttrs = new HashSet<String>();
		JSONMap attributeDefinitionsByNameAndId = conn.api(SSCAttributeDefinitionAPI.class).getAttributeDefinitionHelper().getAttributeDefinitionsByNameAndId();
		for ( SSCSyncAttr attr : SSCSyncAttr.values() ) {
			if ( !attributeDefinitionsByNameAndId.containsKey(attr.getAttributeName()) ) {
				try {
					LOG.info("Creating SSC application version attribute {}",attr.getAttributeName());
					attr.createAttributeDefinition(conn);
				} catch ( Exception e ) {
					// We collect all attribute names that do not exist and cannot be created,
					// to throw a single exception listing all missing attribute names
					missingAttrs.add(String.format("%s (type: %s)", attr.getAttributeName(), attr.getAttributeType()));
				}
			}
		}
		if ( !missingAttrs.isEmpty() ) {
			throw new IllegalStateException(String.format("The following required application attributes are not defined on SSC and cannot be automatically created: %s", missingAttrs.toString()));
		}
	}
}
