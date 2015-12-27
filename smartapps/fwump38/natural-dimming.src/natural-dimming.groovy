/**
 *  Natural Dimming
 *
 *  Copyright 2015 David Mirch
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */


// Automatically generated. Make future change here.
definition(
    name: "Natural Dimming",
    namespace: "fwump38",
    author: "fwump38",
    description: "Set lights to a specified dim level for each mode",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png"
)

preferences {
    page(name: "selectDimmers")
    page(name: "otherSettings")
}

def selectDimmers() {
    dynamicPage(name: "selectDimmers", title: "Dimmers and Modes", nextPage: "otherSettings", uninstall: true) {
        section("Choose Lights to Dim") {
            input "theLights", "capability.switchLevel", 
                multiple: true, 
                title: "Select Switch(es)...", 
                required: false
                }
        section("Set a dimmer level selected for each mode") {
            def m = location.mode
            def myModes = []
            location.modes.each {myModes << "$it"}
            input "modesX", "enum", multiple: true, title: "Select mode(s)", submitOnChange: true, options: myModes.sort(), defaultValue: [m]
            def sortModes = modesX
            if(!sortModes) setModeLevel(m, "level$m")
            else sortModes = sortModes.sort()
            sortModes.each {setModeLevel(it, "level$it")}
            }
    }
}

def setModeLevel(thisMode, modeVar) {
    def result = input modeVar, "number", range: "0..100", title: "Level for $thisMode", required: true
}

def otherSettings() {
	dynamicPage(name:"otherSettings", uninstall: true, install: true) {

        section("Optionally disable dimming if...") {
            input "disableDim", "capability.switch", title: "The following switch is on", required: false
        }
        section {
			label title: "Assign a name:", required: false
		}
   	}
}


def installed()
{
    initialize()
}

def updated()
{
    unsubscribe()
    unschedule()
    initialize()
}

def initialize()
{
    subscribe(theLights, "switch.on", dimHandler)
    subscribe(disableDim, "switch", disableHandler)
    subscribe(theLights, "level", levelHandler)
    state.disabled = "false"
    subscribe(location, modeChangeHandler)
    
    state.modeLevels = [:]
    for(m in modesX) {
        def level = settings.find {it.key == "level$m"}
        state.modeLevels << [(m):level.value]
    }

    state.currentMode = location.mode in modesX ? location.mode : modesX[0]
    state.dimLevel = state.modeLevels[state.currentMode]
}

def disableHandler(evt) {
    if (evt.value == "on") {
        state.disabled = "true"
    } else {
        state.disabled = "false"
    }
}

def setDimmer(dimmer) {   
    if (state.disabled == "false") {
        dimmer.setLevel(state.dimLevel)
    }

}

def dimHandler(evt) {
    //get the dimmer that's been turned on
    def dimmer = dimmers.find{it.id == evt.deviceId}
    log.trace "${dimmer.displayName} was turned on..."
    setDimmer(dimmer)
}

def modeChangeHandler(evt) {
    if(state.currentMode == evt.value || !(evt.value in modesX)) return   // no change or not one of our modes
    state.currentMode = evt.value              
    state.dimLevel = state.modeLevels[evt.value]
}

def levelHandler(evt) {                     // allows a dimmer to change the current dimLevel
    if(evt.value == state.dimLevel) return
    state.dimLevel = evt.value
    switchesOn()
}