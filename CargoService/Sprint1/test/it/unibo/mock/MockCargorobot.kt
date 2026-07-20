/* Test double (unit variant of the Sprint 1 TestPlans, see sprint1_v0.html) */
package it.unibo.mock

import it.unibo.kactor.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import unibo.basicomm23.utils.CommUtils

/**
 * MockCargorobot — replaces the cargorobot adapter in the test configuration
 * (cargoservicetest.pl). It honours the same reachTarget/targetReached contract
 * of the real adapter but confirms every target after a short delay, so that
 * the TestPlans assert on the business logic of cargoservice in isolation,
 * without the robotsmart subsystem running.
 *
 * The constructor signature mirrors the generated actors: it is the one that
 * qak instantiates by reflection (String, CoroutineScope, Boolean, Boolean).
 */
class MockCargorobot ( name: String, scope: CoroutineScope, isconfined: Boolean=false, isdynamic: Boolean=false ) :
          ActorBasicFsm( name, scope, confined=isconfined, dynamically=isdynamic ){

	override fun getInitialState() : String{
		return "waiting"
	}
	override fun getBody() : (ActorBasicFsm.() -> Unit){
		return { //this:ActorBasicFsm
				state("waiting") { //this:State
					action { //it:State
					}
					sysaction { //it:State
					}
					 transition(edgeName="tm00",targetState="serve",cond=whenRequest("reachTarget"))
				}
				state("serve") { //this:State
					action { //it:State
						delay(150)
						CommUtils.outyellow("[MOCK CARGOROBOT] target confirmed (test double)")
						answer("reachTarget", "targetReached", "targetReached(ok)"   )
					}
					sysaction { //it:State
					}
					 transition( edgeName="goto",targetState="waiting", cond=doswitch() )
				}
			}
		}
}
