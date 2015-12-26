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
            paragraph "If any of these people are home, the home mode(s) will change according to the home schedule."
        }
        section("Change Away Modes According To Schedule") {
            input "peopleAway", "capability.presenceSensor", multiple: true, title: "If all of these people are away...", required: true
            input "falseAlarmThreshold", "decimal", title: "Number of minutes", required: false, defaultValue: 10
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
    state.clear()
    subscribe(people, "presence", presence)
    runIn(60, checkSun)
    subscribe(location, "sunrise", setSunrise)
    subscribe(location, "sunset", setSunset)
}

//check current sun state when installed.
def checkSun() {
  def zip     = settings.zip as String
  def sunInfo = getSunriseAndSunset(zipCode: zip)
 def current = now()

if (sunInfo.sunrise.time < current && sunInfo.sunset.time > current) {
    state.sunMode = "sunrise"
   setSunrise()
  }
  
else {
   state.sunMode = "sunset"
    setSunset()
  }
}

//change to sunrise mode on sunrise event
def setSunrise(evt) {
  state.sunMode = "sunrise";
  changeSunMode(newMode);
}

//change to sunset mode on sunset event
def setSunset(evt) {
  state.sunMode = "sunset";
  changeSunMode(newMode)
}

//presence change run logic based on presence state of home
def presence(evt) {
  if(evt.value == "not present") {
    log.debug("Checking if everyone is away")

    if(everyoneIsAway()) {
      log.info("Nobody is home, running away sequence")
      def delay = (falseAlarmThreshold != null && falseAlarmThreshold != "") ? falseAlarmThreshold * 60 : 10 * 60 
      runIn(delay, "setAway")
    }
  }

else {
    def lastTime = state[evt.deviceId]
    if (lastTime == null || now() - lastTime >= 1 * 60000) {
        log.info("Someone is home, running home sequence")
        setHome()
    }    
    state[evt.deviceId] = now()

  }
}

//if empty set home to one of the away modes
//needs work
def setAway() {
  if(everyoneIsAway()) {
    if(state.sunMode == "sunset") {
      def message = "Performing \"${awayNight}\" for you as requested."
      log.info(message)
      sendAway(message)
      location.helloHome.execute(settings.awayNight)
    }
    
    else if(state.sunMode == "sunrise") {
      def message = "Performing \"${awayDay}\" for you as requested."
      log.info(message)
      sendAway(message)
      location.helloHome.execute(settings.awayDay)
      }
    else {
      log.debug("Mode is the same, not evaluating")
    }
  }

  else {
    log.info("Somebody returned home before we set to '${newAwayMode}'")
  }
}
    
//set home mode when house is occupied
//needs work
def setHome() {
log.info("Setting Home Mode!!")
if(anyoneIsHome()) {
      if(state.sunMode == "sunset"){
      if (location.mode != "${homeModeNight}"){
      def message = "Performing \"${homeNight}\" for you as requested."
        log.info(message)
        sendHome(message)
        location.helloHome.execute(settings.homeNight)
        }
       }
       
      if(state.sunMode == "sunrise"){
      if (location.mode != "${homeModeDay}"){
      def message = "Performing \"${homeDay}\" for you as requested."
        log.info(message)
        sendHome(message)
        location.helloHome.execute(settings.homeDay)
            }
      }      
    }
    
}

private everyoneIsAway() {
  def result = true

  if(people.findAll { it?.currentPresence == "present" }) {
    result = false
  }

  log.debug("everyoneIsAway: ${result}")

  return result
}

private anyoneIsHome() {
  def result = false

  if(people.findAll { it?.currentPresence == "present" }) {
    result = true
  }

  log.debug("anyoneIsHome: ${result}")

  return result
}
