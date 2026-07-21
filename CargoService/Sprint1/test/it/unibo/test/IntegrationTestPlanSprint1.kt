package it.unibo.test

import it.unibo.kactor.QakContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import unibo.basicomm23.interfaces.Interaction
import unibo.basicomm23.tcp.TcpClientSupport
import unibo.basicomm23.utils.CommUtils
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * IntegrationTestPlanSprint1 — TP1.6: the complete load cycle against the REAL
 * navigation subsystem (see the "Test plans" section of sprint1_v1.html).
 *
 * Unlike TestPlanSprint1 (unit variant, mock robot), this plan deploys the REAL
 * cargorobot adapter (cargoserviceintegrationtest.pl) and lets every reachTarget
 * leg travel to robotsmart26: it is the run that verifies the OUT-OF-BAND
 * coordinate alignment between the business registry (station coordinates kept
 * by cargoservice) and the map the robot actually navigates — the one thing no
 * unit plan can cover, since the mock confirms any target.
 *
 * PREREQUISITES (Deployment, steps 2-3):
 *   1. the robot environment running: in robotsmart26/yamls
 *      docker compose -f unibobasic26.yaml up   (scene on http://localhost:8090)
 *   2. the navigation service running: gradlew run in robotsmart26/
 *   3. the virtual robot at HOME (restart the environment to reset it).
 *
 * LAUNCH (on demand, excluded from the default 'gradlew test'):
 *      gradlew integrationTest
 */
class IntegrationTestPlanSprint1 {

    companion object {
        private const val HOST = "localhost"
        private const val PORT = 8030            // ctxcargoservice
        private const val ROBOTSMART_HOST = "127.0.0.1"
        private const val ROBOTSMART_PORT = 8020 // reused asset, fixed by the committente

        @BeforeClass
        @JvmStatic
        fun startIntegrationConfiguration() {
            // fail fast, with instructions, if the navigation subsystem is down
            try {
                Socket().use { s -> s.connect(InetSocketAddress(ROBOTSMART_HOST, ROBOTSMART_PORT), 2000) }
            } catch (e: Exception) {
                fail(
                    "robotsmart26 is not reachable on $ROBOTSMART_HOST:$ROBOTSMART_PORT.\n" +
                    "TP1.6 needs the robot environment running (Deployment, steps 2-3):\n" +
                    "  1) in robotsmart26/yamls:  docker compose -f unibobasic26.yaml up\n" +
                    "  2) in robotsmart26:        gradlew run\n" +
                    "then launch again:           gradlew integrationTest"
                )
            }
            CommUtils.outcyan("IntegrationTestPlanSprint1 | starting the integration configuration (cargoserviceintegrationtest.pl)")
            thread(isDaemon = true) {
                runBlocking {
                    QakContext.createContexts(
                        HOST, this, "cargoserviceintegrationtest.pl", "sysRules.pl", "ctxcargoservice"
                    )
                }
            }
            Thread.sleep(5000)  // let the context server and the actors boot
        }
    }

    private var conn: Interaction? = null

    @Before
    fun setup() {
        try {
            conn = TcpClientSupport.connect(HOST, PORT, 10)
        } catch (e: Exception) {
            fail("TCP connection error towards ctxcargoservice ($HOST:$PORT): ${e.message}")
        }
    }

    @After
    fun teardown() {
        conn?.close()
    }

    // ---------------------------------------------------------------- helpers

    private fun request(msgId: String, content: String): String {
        val reply = conn!!.request("msg($msgId,request,testunit,cargoservice,$content,1)")
        assertNotNull("no reply received for $msgId", reply)
        return reply.toString()
    }

    private fun presetHold(config: String) {
        val reply = request("preset_hold", "preset_hold($config)")
        assertTrue("preset_hold($config) not confirmed: $reply", reply.contains("preset_done"))
    }

    /** Blocks until the service is quiescent again: with the real robot this is
        the whole trip (IOPort -> slot5 -> marking pause -> slot -> HOME). */
    private fun getHoldDescription(): String {
        val reply = request("get_hold", "get_hold(none)")
        assertTrue("expected hold_state but got: $reply", reply.contains("hold_state"))
        val m = Regex("""hold_state\(\s*([a-z_]+)\s*\)""").find(reply)
        assertNotNull("could not parse the hold description from: $reply", m)
        return m!!.groupValues[1]
    }

    private fun injectContainerDetected() {
        // detection = three consecutive below-threshold measurements (~3 s)
        repeat(3) {
            conn!!.forward("msg(sonar_distance,event,sonar,none,sonar_distance(1),1)")
            Thread.sleep(300)
        }
    }

    // -------------------------------------------------------------- TestPlan

    /**
     * TP1.6 — Integration: complete load cycle with robotsmart26.
     * Given the robot environment and the navigation service running, the hold
     * empty, the virtual robot at HOME. When an accepted cycle runs (detection
     * injected as in TP1.5). Then every reachTarget leg is confirmed by the
     * navigation subsystem (the virtual robot visibly performs the trip on
     * http://localhost:8090) and at the end the hold shows the reserved slot
     * occupied and the state disengaged.
     *
     * Generous timeout: the real robot moves cell by cell (StepTime 350 ms).
     * NOTE on a failed run: if a leg ends with targetUnreachable (coordinate
     * misalignment with the map!), the hold is marked OUT_OF_SERVICE and this
     * test reports hold_state(out_of_service_...): that is exactly the
     * misalignment this plan exists to catch.
     */
    @Test(timeout = 300000)
    fun test_completeLoadCycleWithRobotsmart() {
        CommUtils.outmagenta("TP1.6: complete load cycle against robotsmart26")
        presetHold("empty")

        val reply = request("load_request", "load_request(none)")
        CommUtils.outgreen("TP1.6 reply: $reply")
        assertTrue("request must be accepted: $reply", reply.contains("load_accepted"))
        val slotId = Regex("""load_accepted\(\s*(\d+)\s*\)""").find(reply)?.groupValues?.get(1)?.toIntOrNull()
        assertNotNull("could not read the reserved slot id from: $reply", slotId)

        injectContainerDetected()
        val hold = getHoldDescription()   // returns only when the real trip is over
        CommUtils.outgreen("TP1.6 hold after the cycle: $hold")

        assertTrue(
            "expected disengaged at the end of the cycle (out_of_service means a " +
            "reachTarget leg FAILED: check the registry/map coordinate alignment): $hold",
            hold.startsWith("disengaged")
        )
        val parts = hold.split("_")
        assertEquals("unexpected hold description: $hold", 5, parts.size)
        assertEquals("the RESERVED slot ($slotId) must be occupied: $hold",
                     "occupied", parts[slotId!!])
    }
}
