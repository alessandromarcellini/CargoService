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
 * TestPlanSprint0 (Sprint 1 regression) — the Sprint 0 plan set, re-run on the
 * OBSERVABLE Sprint 1 architecture.
 *
 * Sprint 0 could only automate TP0.1 (an empty hold is the natural startup
 * state); the refuse (TP0.2) and retrylater (TP0.3) cases were DEFERRED because
 * the hold state could not be preset from outside. On Sprint 1 the service is
 * observable/controllable (preset_hold / get_hold), so all three Sprint 0 plans
 * are now automatable and are kept here as a regression, on exactly the same
 * production contract.
 *
 * It reuses the very same test configuration as TestPlanSprint1
 * (cargoservicetest.pl: the REAL cargoservice + MockCargorobot + MockIoport,
 * co-located in ctxcargoservice, TCP 8030). Because build.gradle runs the test
 * task with forkEvery = 1, this class boots its OWN copy of the configuration in
 * its OWN JVM, so it never clashes with TestPlanSprint1 on port 8030: a single
 * 'gradlew test' runs the Sprint 0 and Sprint 1 plans together.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TestPlanSprint0 {

    companion object {
        private const val HOST = "localhost"
        private const val PORT = 8030          // ctxcargoservice, from cargoservicetest.pl

        @BeforeClass
        @JvmStatic
        fun startTestConfiguration() {
            CommUtils.outcyan("TestPlanSprint0 (regression) | starting the qak test configuration (cargoservicetest.pl)")
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
    // (identical contract to TestPlanSprint1: the service code under test is the
    // production one, only the deployment description changes)

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

    /** Publishes the same distance events the proactive sonar would: the
        detection requires D < DFREE/2 SUSTAINED for three consecutive
        measurements (~3 s at the 1 Hz rate). */
    private fun injectContainerDetected() {
        repeat(3) {
            conn!!.forward("msg(sonar_distance,event,sonar,none,sonar_distance(1),1)")
            Thread.sleep(300)
        }
    }

    private fun slotIdOf(reply: String): Int? =
        Regex("""load_accepted\(\s*(\d+)\s*\)""").find(reply)?.groupValues?.get(1)?.toIntOrNull()

    private fun occupiedCount(holdDesc: String): Int =
        Regex("occupied").findAll(holdDesc).count()

    // -------------------------------------------------------------- TestPlans
    // The three Sprint 0 answers to a load_request, on the Sprint 1 architecture.

    /** TP0.1 — load accepted on an empty hold (the only case Sprint 0 automated). */
    @Test(timeout = 60000)
    fun test1_loadAcceptedOnEmptyHold() {
        presetHold("empty")

        val reply = loadRequest()
        assertTrue("expected load_accepted but got: $reply", reply.contains("load_accepted"))
        assertFalse("wrong branch taken: $reply",
                    reply.contains("load_refused") || reply.contains("load_retrylater"))
        val slotId = slotIdOf(reply)
        assertNotNull("could not read the reserved slot id from: $reply", slotId)
        assertTrue("reserved slot id out of range (expected 1..4): $slotId", slotId!! in 1..4)

        // close the engagement so the service is quiescent again for the next plan
        injectContainerDetected()
        val hold = getHoldDescription()
        assertTrue("system not disengaged at the end of the cycle: $hold", hold.startsWith("disengaged"))
    }

    /** TP0.2 — load refused when the hold is full (deferred from Sprint 0, now automatable). */
    @Test(timeout = 60000)
    fun test2_loadRefusedOnFullHold() {
        presetHold("full")

        val reply = loadRequest()
        assertTrue("expected load_refused but got: $reply", reply.contains("load_refused"))
        assertFalse("wrong branch taken: $reply",
                    reply.contains("load_accepted") || reply.contains("load_retrylater"))

        val hold = getHoldDescription()
        assertTrue("expected disengaged: $hold", hold.startsWith("disengaged"))
        assertEquals("the four slots must still be occupied: $hold", 4, occupiedCount(hold))
    }

    /** TP0.3 — retry later when the system is engaged (deferred from Sprint 0, now automatable). */
    @Test(timeout = 90000)
    fun test3_retryLaterWhenEngaged() {
        presetHold("empty")

        val first = loadRequest()
        assertTrue("first request must be accepted: $first", first.contains("load_accepted"))
        Thread.sleep(500)   // let the service enter the engagement window

        val second = loadRequest()
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
}
