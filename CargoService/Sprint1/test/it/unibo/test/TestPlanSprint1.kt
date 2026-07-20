package it.unibo.test

import it.unibo.kactor.QakContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import unibo.basicomm23.interfaces.Interaction
import unibo.basicomm23.tcp.TcpClientSupport
import unibo.basicomm23.utils.CommUtils
import kotlin.concurrent.thread

/**
 * TestPlanSprint1 — automated system tests for the cargoservice Sprint 1 model
 * (see the "Test plans" section of sprint1_v0.html).
 *
 * TEST ARCHITECTURE (unit variant). The test JVM starts, once, the qak TEST
 * CONFIGURATION described by cargoservicetest.pl: the REAL cargoservice plus
 * two test doubles (MockCargorobot, which confirms every reachTarget;
 * MockIoport, which absorbs the inhibit/enable boundary commands; no sonar is
 * deployed, each plan injects the sonar_distance event it needs). All co-located
 * in ctxcargoservice: by qak location transparency the service code is exactly
 * the production one. The test client then plays the ioport boundary over TCP
 * (localhost:8030) using the same request/reply contract, and injects the
 * below-threshold sonar_distance event where a plan requires the detection.
 *
 * OBSERVABILITY / CONTROLLABILITY (P5). Every Given is built with
 * preset_hold(empty|full) and every Then is asserted on the reply plus on the
 * observable hold description returned by get_hold, e.g.
 * hold_state(disengaged_free_occupied_free_free).
 * Since the service serves get_hold/preset_hold only when it is quiescent
 * (waiting state) and defers them otherwise, a get_hold request also acts as a
 * synchronization barrier: its reply arrives only when the scenario is over.
 *
 * EXACTLY-ONE-REPLY GUARD (P1). All the messages of a test travel on one TCP
 * connection, in order: if a request ever produced a second, spurious reply,
 * that reply would be returned to the NEXT interaction of the test (instead of
 * hold_state / preset_done), turning the test red.
 *
 * Method names are numbered because the plans share the running service:
 * @FixMethodOrder makes the execution order deterministic.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TestPlanSprint1 {

    companion object {
        private const val HOST = "localhost"
        private const val PORT = 8030          // ctxcargoservice, from cargoservice_sprint1.qak

        @BeforeClass
        @JvmStatic
        fun startTestConfiguration() {
            CommUtils.outcyan("TestPlanSprint1 | starting the qak test configuration (cargoservicetest.pl)")
            thread(isDaemon = true) {
                runBlocking {
                    QakContext.createContexts(
                        HOST, this, "cargoservicetest.pl", "sysRules.pl", "ctxcargoservice"
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
            fail("TCP connection error towards the test configuration ($HOST:$PORT): ${e.message}")
        }
    }

    @After
    fun teardown() {
        conn?.close()
    }

    // ---------------------------------------------------------------- helpers

    private fun request(msgId: String, content: String): String {
        val envelope = "msg($msgId,request,testunit,cargoservice,$content,1)"
        val reply = conn!!.request(envelope)
        assertNotNull("no reply received for $msgId", reply)
        return reply.toString()
    }

    private fun loadRequest(): String = request("load_request", "load_request(none)")

    /** Presets the hold (Given); the reply is also a quiescence barrier. */
    private fun presetHold(config: String) {
        val reply = request("preset_hold", "preset_hold($config)")
        assertTrue("preset_hold($config) not confirmed (spurious reply pending?): $reply",
                   reply.contains("preset_done"))
    }

    /** Reads the observable hold description (Then + quiescence barrier). */
    private fun getHoldDescription(): String {
        val reply = request("get_hold", "get_hold(none)")
        assertTrue("expected hold_state but got (spurious reply pending?): $reply",
                   reply.contains("hold_state"))
        val m = Regex("""hold_state\(\s*([a-z_]+)\s*\)""").find(reply)
        assertNotNull("could not parse the hold description from: $reply", m)
        return m!!.groupValues[1]       // e.g. disengaged_free_occupied_free_free
    }

    /** Publishes the same distance event the proactive sonar would:
        D = 1 < DFREE/2, i.e. the detection of a container at the IOPort. */
    private fun injectContainerDetected() {
        conn!!.forward("msg(sonar_distance,event,sonar,none,sonar_distance(1),1)")
    }

    private fun slotIdOf(reply: String): Int? =
        Regex("""load_accepted\(\s*(\d+)\s*\)""").find(reply)?.groupValues?.get(1)?.toIntOrNull()

    private fun occupiedCount(holdDesc: String): Int =
        Regex("occupied").findAll(holdDesc).count()

    // -------------------------------------------------------------- TestPlans

    /**
     * TP1.1 — Load accepted on an empty hold (inherited from TP0.1).
     * Given the hold empty, state disengaged. When one load_request is sent.
     * Then the answer is load_accepted(SLOTID) with SLOTID in 1..4 and exactly
     * one reply is produced.
     */
    @Test(timeout = 60000)
    fun test1_loadAcceptedOnEmptyHold() {
        CommUtils.outmagenta("TP1.1: load_request on an empty hold -> load_accepted(1..4)")
        presetHold("empty")

        val reply = loadRequest()
        CommUtils.outgreen("TP1.1 reply: $reply")
        assertTrue("expected load_accepted but got: $reply", reply.contains("load_accepted"))
        assertFalse("wrong branch taken: $reply",
                    reply.contains("load_refused") || reply.contains("load_retrylater"))
        val slotId = slotIdOf(reply)
        assertNotNull("could not read the reserved slot id from: $reply", slotId)
        assertTrue("reserved slot id out of range (expected 1..4): $slotId", slotId!! in 1..4)

        // close the engagement (detection + full trip) so that the system is
        // quiescent again; getHoldDescription() is also the one-reply guard
        injectContainerDetected()
        val hold = getHoldDescription()
        assertTrue("system not disengaged at the end of the cycle: $hold", hold.startsWith("disengaged"))
    }

    /**
     * TP1.2 — Load refused when the hold is full (deferred from Sprint 0).
     * Given the hold preset full. When one load_request is sent. Then the
     * answer is load_refused, exactly one reply, and the hold is unchanged
     * (four slots occupied, state disengaged).
     */
    @Test(timeout = 60000)
    fun test2_loadRefusedOnFullHold() {
        CommUtils.outmagenta("TP1.2: load_request on a full hold -> load_refused")
        presetHold("full")

        val reply = loadRequest()
        CommUtils.outgreen("TP1.2 reply: $reply")
        assertTrue("expected load_refused but got: $reply", reply.contains("load_refused"))
        assertFalse("wrong branch taken: $reply",
                    reply.contains("load_accepted") || reply.contains("load_retrylater"))

        val hold = getHoldDescription()
        assertTrue("expected disengaged: $hold", hold.startsWith("disengaged"))
        assertEquals("the four slots must still be occupied: $hold", 4, occupiedCount(hold))
    }

    /**
     * TP1.3 — Retry later when the system is engaged (deferred from Sprint 0).
     * Given an accepted request (system engaged). When a second load_request
     * arrives. Then the answer is load_retrylater(engaged), exactly one reply,
     * and the first conversation is unaffected (its trip completes and only
     * its slot results occupied).
     */
    @Test(timeout = 90000)
    fun test3_retryLaterWhenEngaged() {
        CommUtils.outmagenta("TP1.3: second load_request while engaged -> load_retrylater(engaged)")
        presetHold("empty")

        val first = loadRequest()
        assertTrue("first request must be accepted: $first", first.contains("load_accepted"))
        Thread.sleep(500)   // let the service enter the engagement window

        val second = loadRequest()
        CommUtils.outgreen("TP1.3 second reply: $second")
        assertTrue("expected load_retrylater but got: $second", second.contains("load_retrylater"))
        assertTrue("the retrylater answer must carry the engaged state: $second",
                   second.contains("engaged"))
        assertFalse("wrong branch taken: $second",
                    second.contains("load_accepted") || second.contains("load_refused"))

        // the first conversation completes normally
        injectContainerDetected()
        val hold = getHoldDescription()
        assertTrue("expected disengaged: $hold", hold.startsWith("disengaged"))
        assertEquals("only the slot of the FIRST conversation must be occupied: $hold",
                     1, occupiedCount(hold))
    }

    /**
     * TP1.4 — Engagement timeout discards the reservation.
     * Given an accepted request with no container ever placed in the sensor
     * area (no sonar is deployed, no event is injected). When the prefixed
     * 30 s window expires.
     * Then the state is disengaged and all the slots are still free.
     * The get_hold request is deferred by the service until it becomes
     * quiescent, i.e. until the window has expired: the test just waits for
     * the reply.
     */
    @Test(timeout = 90000)
    fun test4_engagementTimeoutDiscardsReservation() {
        CommUtils.outmagenta("TP1.4: engagement timeout (30 s, no detection) -> reservation discarded")
        presetHold("empty")

        val reply = loadRequest()
        assertTrue("request must be accepted: $reply", reply.contains("load_accepted"))

        val start = System.currentTimeMillis()
        val hold = getHoldDescription()   // served only after the 30 s window
        val elapsed = System.currentTimeMillis() - start
        CommUtils.outgreen("TP1.4 hold after ${elapsed} ms: $hold")

        assertTrue("expected disengaged after the timeout: $hold", hold.startsWith("disengaged"))
        assertEquals("the reservation must be discarded (no slot occupied): $hold",
                     0, occupiedCount(hold))
        assertTrue("the window closed too early (${elapsed} ms)", elapsed >= 25000)
    }

    /**
     * TP1.5 — Complete load cycle (unit variant).
     * Given an accepted request on an empty hold. When the detection event is
     * emitted. Then the trip IOPort -> slot5 (marking pause) -> reserved slot
     * -> HOME completes and at the end the hold shows the RESERVED slot
     * occupied and the state disengaged.
     */
    @Test(timeout = 90000)
    fun test5_completeLoadCycle() {
        CommUtils.outmagenta("TP1.5: complete load cycle on the reserved slot")
        presetHold("empty")

        val reply = loadRequest()
        assertTrue("request must be accepted: $reply", reply.contains("load_accepted"))
        val slotId = slotIdOf(reply)
        assertNotNull("could not read the reserved slot id from: $reply", slotId)

        injectContainerDetected()
        val hold = getHoldDescription()   // deferred until the trip is over
        CommUtils.outgreen("TP1.5 hold after the cycle: $hold")

        assertTrue("expected disengaged at the end of the cycle: $hold", hold.startsWith("disengaged"))
        val parts = hold.split("_")       // [disengaged, slot1, slot2, slot3, slot4]
        assertEquals("unexpected hold description: $hold", 5, parts.size)
        assertEquals("the RESERVED slot ($slotId) must be occupied: $hold",
                     "occupied", parts[slotId!!])
        assertEquals("exactly one slot must be occupied: $hold", 1, occupiedCount(hold))
    }
}
