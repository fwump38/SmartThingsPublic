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
    page(name: "selectPeople")
}

def selectRoutines() {
    dynamicPage(name: "selectRoutines", title: "Routine Selection", nextPage: "selectPeople", uninstall: true) {

        section("Set a routine for each mode selected") {
            def m = location.mode
            def myModes = []
            location.modes.each {myModes << "$it"}
            input "modesX", "enum", multiple: true, title: "Select mode(s)", submitOnChange: true, options: myModes.sort(), defaultValue: [m]
            def sortedModesX = modesX
            if(!sortedModesX) {
                state.modesX = [m]
                href(name: "setupSettings", page: selectSettings, title: "Routine Automation")}
                else {
                    sortedModesX = sortedModesX.sort()
                    state.modesX = sortedModesX
                    href(name: "setupSettings", page: selectSettings, title: "Routine Automation")
                }
        }
    }
}

def selectSettings() {
    dynamicPage(name: "selectSettings", title: "Routine Automation", uninstall: true) {
        // get the available actions
        def actions = location.helloHome?.getPhrases()*.label
            if (actions) {
            // sort them alphabetically
            actions.sort()
            }

        section("Select a Hello, Home Routine to assign for each mode") {
            state.modesX.each {input "routine$it", "enum", multiple: false, title: "Routine for $it", submitOnChange: true, required: true, options: actions}
        }
        section("Choose whether each mode is considered Home or Away") {
            state.modesX.each{input "status$it", "enum", multiple: false, title: "Select Home/Away status for $it", submitOnChange: true, required: true, options: ["Home", "Away"]}
        }
        section("Choose when each mode should start") {
            state.modesX.each{input "startingX$it", "enum", title: "Start for $it", options: ["A specific time", "Sunrise", "Sunset"], required: true, defaultValue: "A specific time", submitOnChange: true  
                if(settings."startingX$it" in [null, "A specific time"]) input "starting$it", "time", title: "Start time for $it", required: false
                else {
                    if(settings."startingX$it" == "Sunrise") input "startSunriseOffset$it", "number", range: "*..*", title: "Offset in minutes (+/-) for $it", required: false
                    else if(settings."startingX$it" == "Sunset") input "startSunriseOffset$it", "number", range: "*..*", title: "Offset in minutes (+/-) for $it", required: false
                }
            }
        }
        section("Choose when each mode should end") {
            state.modesX.each{input "endingX$it", "enum", title: "Ending for $it", options: ["A specific time", "Sunrise", "Sunset"], required: true, defaultValue: "A specific time", submitOnChange: true
                if(settings."endingX$it" in [null, "A specific time"]) input "ending$it", "time", title: "End time for $it", required: false
                else {
                    if(settings."endingX$it" == "Sunrise") input "endSunriseOffset$it", "number", range: "*..*", title: "Offset in minutes (+/-) for $it", required: false
                    else if(settings."endingX$it" == "Sunset") input "endSunsetOffset$it", "number", range: "*..*", title: "Offset in minutes (+/-) for $it", required: false
                }
            }
        }
    }

}

def selectPeople() {
    dynamicPage(name: "selectPeople", title: "People Selection", uninstall: true, install: true) {

        section("Change Home Modes According To Schedule") {
            input "peopleHome", "capability.presenceSensor", multiple: true, title: "If any of these people are home...", required: true
            input "falseAlarmThresholdHome", "decimal", title: "Number of minutes", required: false, defaultValue: 10
            paragraph "If any of these people are home, the home mode(s) will change according to the home schedule."
        }
        section("Change Away Modes According To Schedule") {
            input "peopleAway", "capability.presenceSensor", multiple: true, title: "If all of these people are away...", required: true
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