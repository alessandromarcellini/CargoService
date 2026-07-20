/* Test double (unit variant of the Sprint 1 TestPlans, see sprint1_v1.html) */
package it.unibo.mock

import it.unibo.kactor.*
import kotlinx.coroutines.CoroutineScope
import unibo.basicomm23.utils.CommUtils

/**
 * MockIoport — replaces the customer boundary in the test configuration
 * (cargoservicetest.pl). It absorbs the inhibit_ioport/enable_ioport dispatches
 * that cargoservice sends around every load operation, so that the service code
 * under test is exactly the production one while the test client plays the
 * customer role directly over TCP.
 */
class MockIoport ( name: String, scope: CoroutineScope, isconfined: Boolean=false, isdynamic: Boolean=false ) :
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
					 transition(edgeName="tmi00",targetState="absorb",cond=whenDispatch("inhibit_ioport"))
					transition(edgeName="tmi01",targetState="absorb",cond=whenDispatch("enable_ioport"))
				}
				state("absorb") { //this:State
					action { //it:State
						CommUtils.outyellow("[MOCK IOPORT] boundary command absorbed (test double)")
					}
					sysaction { //it:State
					}
					 transition( edgeName="goto",targetState="waiting", cond=doswitch() )
				}
			}
		}
}
