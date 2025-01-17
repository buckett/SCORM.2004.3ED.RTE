/**********************************************************************************
 * $URL:  $
 * $Id:  $
 ***********************************************************************************
 *
 * Copyright (c) 2007 The Sakai Foundation.
 * 
 * Licensed under the Educational Community License, Version 1.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 *      http://www.opensource.org/licenses/ecl1.php
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.wicket.model;

import org.apache.wicket.model.PropertyModel;

public abstract class DecoratedPropertyModel extends PropertyModel {

	private static final long serialVersionUID = 1L;
	
	public DecoratedPropertyModel(Object modelObject, String expression) {
		super(modelObject, expression);
	}

	public Object getObject() {
		Object propertyValue = super.getObject();
		return convertObject(propertyValue);
	}
	
	public abstract Object convertObject(Object object);

}
