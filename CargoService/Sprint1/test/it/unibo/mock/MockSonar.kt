/* Test double (unit variant of the Sprint 1 TestPlans, see sprint1_v0.html) */
package it.unibo.mock

import it.unibo.kactor.*
import kotlinx.coroutines.CoroutineScope
import unibo.basicomm23.utils.CommUtils

/**
 * MockSonar — replaces the simulated sonar in the test configuration
 * (cargoservicetest.pl). It absorbs the checkMeasurement dispatch and NEVER
 * emits containerPositioned: the detection is injected by the test client as
 * an external event, so that every TestPlan (in particular TP1.4, the
 * engagement timeout) is deterministic instead of depending on the random
 * distance produced by the simulated detector.
 */
class MockSonar ( name: String, scope: CoroutineScope, isconfined: Boolean=false, isdynamic: Boolean=false ) :
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
					 transition(edgeName="tms00",targetState="absorb",cond=whenDispatch("checkMeasurement"))
				}
				state("absorb") { //this:State
					action { //it:State
						CommUtils.outyellow("[MOCK SONAR] checkMeasurement absorbed (no detection is simulated)")
					}
					sysaction { //it:State
					}
					 transition( edgeName="goto",targetState="waiting", cond=doswitch() )
				}
			}
		}
}
