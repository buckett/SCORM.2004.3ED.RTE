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


// Wicket Namespace -- we need to ensure that some stuff is there before we proceed -- this top block of code is borrowed
// from the wicket-ajax.js file under the Apache Wicket project - copyright above does not apply
if (typeof(Wicket) == "undefined") {
	Wicket = { };

	Wicket.$=function(arg) {
		if (arguments.length > 1) {
			var e=[];
			for (var i=0; i<arguments.length; i++) {
				e.push(Wicket.$(arguments[i]));
			}
			return e;
		} else if (typeof arg == 'string') {
			return document.getElementById(arg);
		} else {
			return arg;
		}
	}
	
	Wicket.emptyFunction = function() { };
	
	Wicket.Class = {
		create: function() {
			return function() {
				this.initialize.apply(this, arguments);
			}
		}
	}
}

// AjaxRolloverImageButton functionality -- copyright applies to the code below this line
Wicket.AjaxRolloverImageButtonManager = Wicket.Class.create();

Wicket.AjaxRolloverImageButtonManager.prototype = {

	initialize : function() 
	{
		this.activeSrcList = new Array();
		this.inactiveSrcList = new Array();
	},
	
	registerBtn : function(btnId, activeSrc, inactiveSrc)
	{
		this.activeSrcList[btnId] = new Image();
		this.activeSrcList[btnId].src = activeSrc;
		this.inactiveSrcList[btnId] = new Image();
		this.inactiveSrcList[btnId].src = inactiveSrc;
	
		/*var buttonObject = document.getElementById(btnId);
		
		if (typeof(buttonObject) != "undefined" && buttonObject.disabled != "disabled") {
			buttonObject.onmouseover = this.activateBtn(btnId);
			buttonObject.onmouseout = this.inactivateBtn(btnId);
		}*/
		
		return true;
	},
	
	activateBtn : function(btnId)
	{
		var imgsrc = this.activeSrcList[btnId];
	
		var btn = document.getElementById(btnId);
	
		if (btn) {
			if (imgsrc) {
				btn.src = imgsrc.src;
			}
		}
	},
	
	inactivateBtn : function(btnId)
	{
		var imgsrc = this.inactiveSrcList[btnId];
	
		var btn = document.getElementById(btnId);
	
		if (btn) {
			if (imgsrc) {
				btn.src = imgsrc.src;
			}
		}
	}
}

Wicket.ajaxRolloverImageButtonManager = new Wicket.AjaxRolloverImageButtonManager();

Wicket.registerAjaxRolloverImageButton = function(btnId, activeSrc, inactiveSrc) {
	Wicket.ajaxRolloverImageButtonManager.registerBtn(btnId, activeSrc, inactiveSrc);
}



