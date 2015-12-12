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
            if(!sortModes) {def statusLabelValue$m = statusLabel(m, "status$m")
                def hrefParams = [thisMode: m, modeStatus: "status$m"]
                href "setModeStatus", params: hrefParams, title: "Set Status for $m", description: statusLabelValue$m ?: "Tap to set", state: statusLabelValue$m ? "complete" : null}
            else sortModes = sortModes.sort()
            sortModes.each {setModeRoutine(it, "routine$it")}
            sortModes.each {def statusLabelValue$it = statusLabel(m, "status$m")
                def hrefParams = [thisMode: it, modeStatus: "status$it"]
                href "setModeStatus", params: hrefParams, title: "Set Status for $it", description: statusLabelValue$it ?: "Tap to set", state: statusLabelValue$it ? "complete" : null}
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
            input "${params?.modeStatus}", "enum", title: "Should ${params?.thisMode} be considered as", options: ["Home", "Away"], defaultValue: "Home", submitOnChange: true
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
            if(!sortModes) {def timeLabel$m = timeIntervalLabel(m, "modeStart$m", "modeEnd$m")
                def hrefParams = [thisMode: m, modeStart: "modeStart$m", modeEnd: "modeEnd$m"]
                href "modeTimeRange", params: hrefParams, title: "Set a time range for $m", description: timeLabel$m ?: "Tap to set", state: timeLabel$m ? "complete" : null}
            else sortModes = sortModes.sort()
            sortModes.each {
                def timeLabel$it = timeIntervalLabel(it, "modeStart$it", "modeEnd$it")
                def hrefParams = [thisMode: it, modeStart: "modeStart$it", modeEnd: "modeEnd$it"]
                href "modeTimeRange", params: hrefParams, title: "Set a time range for $it", description: timeLabel$it ?: "Tap to set", state: timeLabel$it ? "complete" : null}
        }

    }
}

def modeTimeRange(params) {
    dynamicPage(name:"modeTimeRange",title: "Time Range Selection for ${params?.thisMode}", uninstall: false) {
        section() {
            input "${params?.modeStart}", "enum", title: "Set a start time for ${params?.thisMode}", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: "A specific time", submitOnChange: true
            if(${params?.modeStart} in [null, "A specific time"]) input "starting", "time", title: "Start time", required: false
            else {
                if(${params?.modeStart} == "Sunrise") input "startSunriseOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
                else if(${params?.modeStart} == "Sunset") input "startSunsetOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
            }
        }
        
        section() {
            input "${params?.modeEnd}", "enum", title: "Set an end time for ${params?.thisMode}", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: "A specific time", submitOnChange: true
            if(${params?.modeEnd} in [null, "A specific time"]) input "ending", "time", title: "End time", required: false
            else {
                if(${params?.modeEnd} == "Sunrise") input "endSunriseOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
                else if(${params?.modeEnd} == "Sunset") input "endSunsetOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
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
    modeOk && daysOk && timeOk
}

private getModeOk() {
    def result = !modes || modes.contains(location.mode)
//  log.trace "modeOk = $result"
    return result
}

private getDaysOk() {
    def result = true
    if (days) {
        def df = new java.text.SimpleDateFormat("EEEE")
        if (location.timeZone) df.setTimeZone(location.timeZone)
        else df.setTimeZone(TimeZone.getTimeZone("America/New_York"))
        def day = df.format(new Date())
        result = days.contains(day)
    }
//  log.trace "daysOk = $result"
    return result
}

private getTimeOk() {
    def result = true
    if ((starting && ending) ||
    (starting && ${params?.modeEnd} in ["Sunrise", "Sunset"]) ||
    (${params?.modeStart} in ["Sunrise", "Sunset"] && ending) ||
    (${params?.modeStart} in ["Sunrise", "Sunset"] && ${params?.modeEnd} in ["Sunrise", "Sunset"])) {
        def currTime = now()
        def start = null
        def stop = null
        def s = getSunriseAndSunset(zipCode: zipCode, sunriseOffset: startSunriseOffset, sunsetOffset: startSunsetOffset)
        if(${params?.modeStart} == "Sunrise") start = s.sunrise.time
        else if(${params?.modeStart} == "Sunset") start = s.sunset.time
        else if(starting) start = timeToday(starting,location.timeZone).time
        s = getSunriseAndSunset(zipCode: zipCode, sunriseOffset: endSunriseOffset, sunsetOffset: endSunsetOffset)
        if(${params?.modeEnd} == "Sunrise") stop = s.sunrise.time
        else if(${params?.modeEnd} == "Sunset") stop = s.sunset.time
        else if(ending) stop = timeToday(ending,location.timeZone).time
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
    (starting || ending || days || modes || ${params?.modeStart} || ${params?.modeEnd} || disabled) ? false : true
}

private statusLabel(modeVar, modeStatus) {
    def result = ""
    if (modeStatus == "Home") result = "Home"
    else if (modeStatus == "Away") result = "Away"
}

private offset(value) {
    def result = value ? ((value > 0 ? "+" : "") + value + " min") : ""
}

private timeIntervalLabel(modeVar, modeStart, modeEnd) {
    def result = ""
    if (modeStart == "Sunrise" && modeEnd == "Sunrise") result = "Sunrise" + offset(startSunriseOffset) + " to Sunrise" + offset(endSunriseOffset)
    else if (modeStart == "Sunrise" && modeEnd == "Sunset") result = "Sunrise" + offset(startSunriseOffset) + " to Sunset" + offset(endSunsetOffset)
    else if (modeStart == "Sunset" && modeEnd == "Sunrise") result = "Sunset" + offset(startSunsetOffset) + " to Sunrise" + offset(endSunriseOffset)
    else if (modeStart == "Sunset" && modeEnd == "Sunset") result = "Sunset" + offset(startSunsetOffset) + " to Sunset" + offset(endSunsetOffset)
    else if (modeStart == "Sunrise" && ending) result = "Sunrise" + offset(startSunriseOffset) + " to " + hhmm(ending, "h:mm a z")
    else if (modeStart == "Sunset" && ending) result = "Sunset" + offset(startSunsetOffset) + " to " + hhmm(ending, "h:mm a z")
    else if (starting && modeEnd == "Sunrise") result = hhmm(starting) + " to Sunrise" + offset(endSunriseOffset)
    else if (starting && modeEnd == "Sunset") result = hhmm(starting) + " to Sunset" + offset(endSunsetOffset)
    else if (starting && ending) result = hhmm(starting) + " to " + hhmm(ending, "h:mm a z")
}