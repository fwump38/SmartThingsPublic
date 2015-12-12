/**
 *  Smart Routines
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
 *
 */
definition(
    name: "Smart Routines",
    namespace: "fwump38",
    author: "David Mirch",
    description: "An app to assign routines to modes and automatically switch between modes based on time and presence.",
    category: "Mode Magic",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
    page(name: "selectRoutines")
    page(name: "selectTimes")
    page(name: "selectPeople")
    page(name: "modeTimeRange")
    page(name: "setModeStatus")
}

def selectRoutines() {
    dynamicPage(name: "selectRoutines", title: "Routine Selection", nextPage: "selectTimes", uninstall: true) {

        section("Set a routine for each mode selected") {
            def m = location.mode
            def myModes = []
            location.modes.each {myModes << "$it"}
            input "modesX", "enum", multiple: true, title: "Select mode(s)", submitOnChange: true, options: myModes.sort(), defaultValue: [m]
            def sortModes = modesX
            if(!sortModes) setModeRoutine(m, "routine$m")
            if(!sortModes) {def hrefParams = [thisMode: m, modeStatus: "status$m"]
                href "setModeStatus", params: hrefParams, title: "Set Status $m"}
            else sortModes = sortModes.sort()
            sortModes.each {setModeRoutine(it, "routine$it")}
            sortModes.each {def hrefParams = [thisMode: it, modeStatus: "status$it"]
                href "setModeStatus", params: hrefParams, title: "Set Status $it"}
        }
    }
}

def setModeRoutine(thisMode, modeRoutine) {
     // get the available actions
    def actions = location.helloHome?.getPhrases()*.label
    if (actions) {
        // sort them alphabetically
        actions.sort()
    }
    def result = input modeRoutine, "enum", multiple: false, title: "Routine for $thisMode", required: true, options: actions
}

def setModeStatus(params) {
    dynamicPage(name:"setModeStatus",title: "Set a status for ${params?.thisMode}", uninstall: false) {
        section() {
            input "modeHomeAway", "enum", title: "Should ${params?.thisMode} be considered as", options: ["Home", "Away"], defaultValue: "Home", submitOnChange: true
        }
    }
}

def selectTimes() {
    dynamicPage(name:"selectTimes",title: "Time Range Selection", nextPage: "selectPeople", uninstall: true) {

        section("Set a time range for each mode selected") {
            def m = location.mode
            def myModes = []
            location.modes.each {myModes << "$it"}
            input "modesX", "enum", multiple: true, title: "Select mode(s)", submitOnChange: true, options: myModes.sort(), defaultValue: [m]
            def sortModes = modesX
            if(!sortModes) {def timeLabel = timeIntervalLabel()
                def hrefParams = [thisMode: m, modeTime: "modeTime$m"]
                href "modeTimeRange", params: hrefParams, title: "Set a time range for $m", description: timeLabel ?: "Tap to set", state: timeLabel ? "complete" : null}
            else sortModes = sortModes.sort()
            sortModes.each {
                def timeLabel = timeIntervalLabel()
                def hrefParams = [thisMode: it, modeTime: "modeTime$it"]
                href "modeTimeRange", params: hrefParams, title: "Set a time range for $it", description: timeLabel ?: "Tap to set", state: timeLabel ? "complete" : null}
        }

    }
}

def modeTimeRange(params) {
    dynamicPage(name:"modeTimeRange",title: "Set a start time for ${params?.thisMode}", uninstall: false) {
        section() {
            input "startingX", "enum", title: "Starting at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: "A specific time", submitOnChange: true
            if(startingX in [null, "A specific time"]) input "starting", "time", title: "Start time", required: false
            else {
                if(startingX == "Sunrise") input "startSunriseOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
                else if(startingX == "Sunset") input "startSunsetOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
            }
        }
        
        section() {
            input "endingX", "enum", title: "Set an end time for ${params?.thisMode}", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: "A specific time", submitOnChange: true
            if(endingX in [null, "A specific time"]) input "ending", "time", title: "End time", required: false
            else {
                if(endingX == "Sunrise") input "endSunriseOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
                else if(endingX == "Sunset") input "endSunsetOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
            }
        }
    }
}

def selectPeople() {
    dynamicPage(name: "selectPeople", title: "People Selection", uninstall: true, install: true) {

        section("Change Home Modes According To Schedule") {
            input "peopleHome", "capability.presenceSensor", multiple: true, title: "If any of these people are home..."
            input "falseAlarmThresholdHome", "decimal", title: "Number of minutes", required: false, defaultValue: 10
            paragraph "If any of these people are home, the mode will change according to the home schedule."
        }
        section("Change Away Modes According To Schedule") {
            input "peopleAway", "capability.presenceSensor", multiple: true, title: "If all of these people are away..."
            input "falseAlarmThresholdAway", "decimal", title: "Number of minutes", required: false, defaultValue: 10
            paragraph "If all of these people leave, the mode will change according to the away schedule."
        }
    }
}

def modeTime() {
    dynamicPage(name:"modeTime",title: "Set a time range for $thisMode", uninstall: false) {
        section() {
            input "startingX", "enum", title: "Starting at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: "A specific time", submitOnChange: true
            if(startingX in [null, "A specific time"]) input "starting", "time", title: "Start time", required: false
            else {
                if(startingX == "Sunrise") input "startSunriseOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
                else if(startingX == "Sunset") input "startSunsetOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
            }
        }
        
        section() {
            input "endingX", "enum", title: "Ending at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: "A specific time", submitOnChange: true
            if(endingX in [null, "A specific time"]) input "ending", "time", title: "End time", required: false
            else {
                if(endingX == "Sunrise") input "endSunriseOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
                else if(endingX == "Sunset") input "endSunsetOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
            }
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}


def initialize() {
    state.currentMode = location.mode in modesX ? location.mode : modesX[0]
}