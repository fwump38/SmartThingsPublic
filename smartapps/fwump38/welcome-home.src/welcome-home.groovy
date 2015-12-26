/**
 *  Welcome Home
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
    name: "Welcome Home",
    namespace: "fwump38",
    author: "David Mirch",
    description: "This will turn on certain lights/switches when an open/close sensor is opened and turn off after a period of time once the sensor is inactive. You can also specify a switch which will prevent this from triggering (for example a TV is on because someone is watching it).",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
    section("Turn on when one of these doors opens"){
        input "contactsX", "capability.contactSensor", multiple: true, title: "Select Doors", required: false
    }
    section("And off after..."){
        input "minutesX", "number", title: "Minutes?", defaultValue: "5"
    }
    section("Turn on/off light(s)..."){
        input "switchesX", "capability.switch", multiple: true, title: "Select Lights"
    }
    section("And Optionally Don't Trigger If Any of These Switches Are On..."){
        input "otherSwitches", "capability.switch", multiple: true, title: "Select Switch(es)"
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

    subscribe(switchesX, "switch", switchChange)
    subscribe(contactsX, "contact", contactHandler)
    subscribe(otherSwitches, "switch", statusChange)
    state.otherSwitches = otherSwitchState()
    schedule("0 * * * * ?", "scheduleCheck")
    state.myState = "ready"
    log.debug "state: " + state.myState
}

def statusChange(evt) {
    log.debug "statusChange: $evt.name: $evt.value"
    
    if(evt.value == "on") {
        state.otherSwitches = otherSwitchState()
        log.debug "otherSwitches: ${state.otherSwitches}"
    }
    else {
        state.otherSwitches = otherSwitchState()
        log.debug "otherSwitches: ${state.otherSwitches}"
    }
}

def otherSwitchState() {
    def otherOn = otherSwitches.findAll { it?.latestValue("switch") == "on" }
    if (otherOn.size>0) {
        return "on"
    }
    else {
        return "off"
    }
}

def switchChange(evt) {
    log.debug "SwitchChange: $evt.name: $evt.value"
    
    if(evt.value == "on") {
        // Slight change of Race condition between motion or contact turning the switch on,
        // versus user turning the switch on. Since we can't pass event parameters :-(, we rely
        // on the state and hope the best.
        if(state.myState == "activating") {
            // OK, probably an event from Activating something, and not the switch itself. Go to Active mode.
            state.myState = "active"
        } else if(state.myState != "active") {
            state.myState = "already on"
        }
    } else {
        // If active and switch is turned off manually, then stop the schedule and go to ready state
        if(state.myState == "active" || state.myState == "activating") {
            unschedule()
        }
        state.myState = "ready"
    }
    log.debug "state: " + state.myState
}

def contactHandler(evt) {
    log.debug "contactHandler: $evt.name: $evt.value"
    
    if (evt.value == "open") {
        if (state.otherSwitches == "off") {
            if (state.myState == "ready") {
                log.debug "Turning on lights by contact opening"
                switches.on()
                state.inactiveAt = null
                state.myState = "activating"
            }
        }
    } else if (evt.value == "closed") {
        if (!state.inactiveAt && state.myState == "active" || state.myState == "activating") {
            // When contact closes, we reset the timer if not already set
            setActiveAndSchedule()
        }
    }
    log.debug "state: " + state.myState
}

def setActiveAndSchedule() {
    unschedule()
    state.myState = "active"
    state.inactiveAt = now()
    schedule("0 * * * * ?", "scheduleCheck")
}

def scheduleCheck() {
    log.debug "schedule check, ts = ${state.inactiveAt}"
    if(state.myState != "already on") {
        if(state.inactiveAt != null) {
            def elapsed = now() - state.inactiveAt
            log.debug "${elapsed / 1000} sec since motion stopped"
            def threshold = 1000 * 60 * minutes1
            if (elapsed >= threshold) {
                if (state.myState == "active") {
                    log.debug "turning off lights"
                    switches.off()
                }
                state.inactiveAt = null
                state.myState = "ready"
            }
        }
    }
    log.debug "state: " + state.myState
}

