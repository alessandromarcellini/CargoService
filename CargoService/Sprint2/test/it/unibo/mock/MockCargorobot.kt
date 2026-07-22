package it.unibo.mock

import it.unibo.kactor.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import unibo.basicomm23.utils.CommUtils

/**
 * MockCargorobot - test double for the cargorobot adapter (Sprint 2).
 * Same constructor shape as the generated actors (qak instantiates it by
 * reflection) and the same reachTarget/targetReached contract as the real
 * adapter, but it confirms EVERY target, so the TestPlans isolate the business
 * logic (no robotsmart subsystem needed).
 */
class MockCargorobot(
    name: String,
    scope: CoroutineScope,
    isconfined: Boolean = false,
    isdynamic: Boolean = false
) : ActorBasicFsm(name, scope, confined = isconfined, dynamically = isdynamic) {

    override fun getInitialState(): String = "waiting"

    override fun getBody(): (ActorBasicFsm.() -> Unit) {
        return {
            state("waiting") {
                action { }
                sysaction { }
                transition(edgeName = "tm00", targetState = "serve", cond = whenRequest("reachTarget"))
            }
            state("serve") {
                action {
                    delay(150)
                    CommUtils.outyellow("[MOCK CARGOROBOT] target confirmed (test double)")
                    answer("reachTarget", "targetReached", "targetReached(ok)")
                }
                sysaction { }
                transition(edgeName = "goto", targetState = "waiting", cond = doswitch())
            }
        }
    }
}
