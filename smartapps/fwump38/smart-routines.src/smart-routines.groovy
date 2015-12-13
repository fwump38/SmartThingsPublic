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
    page(name: "selectSettings")
    page(name: "selectTimes")
    page(name: "selectPeople")
}

def selectRoutines() {
    dynamicPage(name: "selectRoutines", title: "Routine Selection", nextPage: "selectPeople", uninstall: true) {

        section("Set a routine for each mode selected") {
            def m = location.mode
            def myModes = []
            location.modes.each {myModes << "$it"}
            input "modesX", "enum", multiple: true, title: "Select mode(s)", submitOnChange: true, options: myModes.sort(), defaultValue: [m]
            def sortModes = modesX
            if(!sortModes) setModeSettings(m, "routine$m", "status$m" , "startingX$m", "endingX$m", "starting$m", "ending$m", "startsunriseoffset$m", "startsunsetoffset$m", "endsunriseoffset$m", "endsunsetoffset$m")
            else sortModes = sortModes.sort()
            sortModes.each {setModeSettings(m, "routine$it", "status$it" , "startingX$it", "endingX$it", "starting$it", "ending$it", "startsunriseoffset$it", "startsunsetoffset$it", "endsunriseoffset$it", "endsunsetoffset$it")}

        }
    }
}

def selectSettings(thisMode, modeRoutine, modeStatus, modeStartingX, modeEndingX, modeStarting, modeEnding, modeStartSunriseOffset, modeStartSunsetOffset, modeEndSunriseOffset, modeEndSunsetOffset) {
    dynamicPage(name: "selectSettings", title: "Routine Automation for $thisMode", uninstall: true) {
        // get the available actions
        def actions = location.helloHome?.getPhrases()*.label
            if (actions) {
            // sort them alphabetically
            actions.sort()
            }
        def routine = input modeRoutine, "enum", multiple: false, title: "Routine for $thisMode", required: true, options: actions

        section("Select a Hello, Home Routine to assign for each mode selected") {
            input modeRoutine, "enum", multiple: false, title: "Routine for $thisMode", required: true, options: actions
        }
        section("Choose whether $thisMode is considered Home or Away") {
            input sortModes, "enum", multiple: false, title: "Select Home/Away status for $thisMode", submitOnChange: true, options: ["Home", "Away"]
        }
        section("Choose when $thisMode should start") {
            input "modeStartingX", "enum", title: "Starting at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: "A specific time", submitOnChange: true
            if(modeStartingX in [null, "A specific time"]) input "modeStarting", "time", title: "Start time", required: false
            else {
                if(modeStartingX == "Sunrise") input "modeStartSunriseOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
                else if(modeStartingX == "Sunset") input "modeStartSunsetOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
            }
        }
        section("Choose when $thisMode should end") {
            input "modeEndingX", "enum", title: "Ending at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: "A specific time", submitOnChange: true
            if(modeEndingX in [null, "A specific time"]) input "modeEnding", "time", title: "End time", required: false
            else {
                if(modeEndingX == "Sunrise") input "modeEndSunriseOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
                else if(modeEndingX == "Sunset") input "modeEndSunsetOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
            }
        }

    }
}

def selectPeople() {
    dynamicPage(name: "selectPeople", title: "People Selection", uninstall: true, install: true) {

        section("Change Home Modes According To Schedule") {
            input "peopleHome", "capability.presenceSensor", multiple: true, title: "If any of these people are home..."
            input "falseAlarmThresholdHome", "decimal", title: "Number of minutes", required: false, defaultValue: 10
            paragraph "If any of these people are home, the home mode(s) will change according to the home schedule."
        }
        section("Change Away Modes According To Schedule") {
            input "peopleAway", "capability.presenceSensor", multiple: true, title: "If all of these people are away..."
            input "falseAlarmThresholdAway", "decimal", title: "Number of minutes", required: false, defaultValue: 10
            paragraph "If all of these people leave, the away mode(s) will change according to the away schedule."
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

// execution filter methods
private getAllOk() {
    timeOk
}

private getTimeOk() {
    def result = true
    if ((modeStarting && modeEnding) ||
    (modeStarting && modeEnd in ["Sunrise", "Sunset"]) ||
    (modeStart in ["Sunrise", "Sunset"] && modeEnding) ||
    (modeStart in ["Sunrise", "Sunset"] && modeEnd in ["Sunrise", "Sunset"])) {
        def currTime = now()
        def start = null
        def stop = null
        def s = getSunriseAndSunset(zipCode: zipCode, sunriseOffset: modeStartSunriseOffset, sunsetOffset: modeStartSunsetOffset)
        if(modeStart == "Sunrise") start = s.sunrise.time
        else if(modeStart == "Sunset") start = s.sunset.time
        else if(modeStarting) start = timeToday(modeStarting,location.timeZone).time
        s = getSunriseAndSunset(zipCode: zipCode, sunriseOffset: modeEndSunriseOffset, sunsetOffset: modeEndSunsetOffset)
        if(modeEnd == "Sunrise") stop = s.sunrise.time
        else if(modeEnd == "Sunset") stop = s.sunset.time
        else if(modeEnding) stop = timeToday(modeEnding,location.timeZone).time
        result = start < stop ? currTime >= start && currTime <= stop : currTime <= stop || currTime >= start
    }
//  log.trace "getTimeOk = $result"
    return result
}

private hhmm(time, fmt = "h:mm a") {
    def t = timeToday(time, location.timeZone)
    def f = new java.text.SimpleDateFormat(fmt)
    f.setTimeZone(location.timeZone ?: timeZone(time))
    f.format(t)
}

private hideOptionsSection() {
    (modeStarting || modeEnding || days || modes || modeStart || modeEnd || disabled) ? false : true
}

private offset(value) {
    def result = value ? ((value > 0 ? "+" : "") + value + " min") : ""
}

private timeIntervalLabel(modeVar, modeStart, modeEnd) {
    def result = ""
    if (modeStart == "Sunrise" && modeEnd == "Sunrise") result = "Sunrise" + offset(modeStartSunriseOffset) + " to Sunrise" + offset(modeEndSunriseOffset)
    else if (modeStart == "Sunrise" && modeEnd == "Sunset") result = "Sunrise" + offset(modeStartSunriseOffset) + " to Sunset" + offset(modeEndSunsetOffset)
    else if (modeStart == "Sunset" && modeEnd == "Sunrise") result = "Sunset" + offset(modeStartSunsetOffset) + " to Sunrise" + offset(modeEndSunriseOffset)
    else if (modeStart == "Sunset" && modeEnd == "Sunset") result = "Sunset" + offset(modeStartSunsetOffset) + " to Sunset" + offset(modeEndSunsetOffset)
    else if (modeStart == "Sunrise" && modeEnding) result = "Sunrise" + offset(modeStartSunriseOffset) + " to " + hhmm(modeEnding, "h:mm a z")
    else if (modeStart == "Sunset" && modeEnding) result = "Sunset" + offset(modeStartSunsetOffset) + " to " + hhmm(modeEnding, "h:mm a z")
    else if (modeStarting && modeEnd == "Sunrise") result = hhmm(modeStarting) + " to Sunrise" + offset(modeEndSunriseOffset)
    else if (modeStarting && modeEnd == "Sunset") result = hhmm(modeStarting) + " to Sunset" + offset(modeEndSunsetOffset)
    else if (modeStarting && modeEnding) result = hhmm(modeStarting) + " to " + hhmm(modeEnding, "h:mm a z")
}