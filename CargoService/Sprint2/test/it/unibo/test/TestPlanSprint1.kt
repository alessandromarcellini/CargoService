package it.unibo.test

import it.unibo.kactor.QakContext
import kotlinx.coroutines.runBlocking
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import unibo.basicomm23.interfaces.Interaction
import unibo.basicomm23.tcp.TcpClientSupport
import unibo.basicomm23.utils.CommUtils
import java.util.Collections
import kotlin.concurrent.thread

/**
 * TestPlanSprint1 (Sprint 2 regression) — the Sprint 1 plan set, re-run on the
 * Sprint 2 MQTT push architecture.
 *
 * The Sprint 1 plans (TP1.1..TP1.5) were driven over TCP with a pull get_hold.
 * Sprint 2 replaced pull observation with the pushed `hold_state` feed and moved
 * the boundary request onto the `load_requests` topic, so the same scenarios are
 * re-expressed here on the current carrier (as TestPlanSprint2 does). This class
 * also supplies the refuse-on-full (TP1.2) and the engagement-timeout (TP1.4)
 * cases, which are not otherwise present in the TP2.x list, so the current
 * design is checked against the whole verified history.
 *
 * PREREQUISITE: the Mosquitto broker MUST be running (tcp://localhost:1883).
 * build.gradle runs the test task with forkEvery = 1, so this class boots its
 * own copy of the qak test configuration in its own JVM (TCP 8030).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TestPlanSprint1 {

    companion object {
        private const val HOST = "localhost"
        private const val TCP_PORT = 8030                 // ctxcargoservice (preset_hold)
        private const val BROKER = "tcp://localhost:1883"

        private const val TOPIC_LOAD = "load_requests"
        private const val TOPIC_HOLD = "hold_state"
        private const val TOPIC_SONAR = "sonar_data"
        private const val TOPIC_LED = "led_data"

        private lateinit var mqtt: MqttClient

        @Volatile
        private var lastHold: String = ""
        private val ledCmds = Collections.synchronizedList(ArrayList<String>())
        private var seq = 1

        @BeforeClass
        @JvmStatic
        fun startAll() {
            CommUtils.outcyan("TestPlanSprint1 (regression) | starting the qak test configuration (cargoservicetest.pl)")
            thread(isDaemon = true) {
                runBlocking {
                    QakContext.createContexts(HOST, this, "cargoservicetest.pl", "sysRules.pl", "ctxcargoservice")
                }
            }
            Thread.sleep(6000) // let the context server and the actors boot

            mqtt = MqttClient(BROKER, "testplan1_" + System.currentTimeMillis(), MemoryPersistence())
            val opts = MqttConnectOptions().apply { isCleanSession = true }
            mqtt.connect(opts)
            mqtt.subscribe(TOPIC_HOLD) { _, m ->
                val payload = String(m.payload)
                val match = Regex("""holdstatus\(([^)]*)\)""").find(payload)
                if (match != null) lastHold = match.groupValues[1].replace(" ", "")
            }
            mqtt.subscribe(TOPIC_LED) { _, m -> ledCmds.add(String(m.payload).trim()) }
            Thread.sleep(1000)
        }

        @AfterClass
        @JvmStatic
        fun stopAll() {
            try { if (Companion::mqtt.isInitialized && mqtt.isConnected) mqtt.disconnect() } catch (_: Exception) {}
        }
    }

    // ---------------------------------------------------------------- helpers
    // (identical to TestPlanSprint2: same push contract, same test-support hooks)

    private fun presetHold(config: String) {
        val conn: Interaction = TcpClientSupport.connect(HOST, TCP_PORT, 10)
        try {
            val env = "msg(preset_hold,request,testunit,cargoservice,preset_hold($config),1)"
            val reply = conn.request(env)
            assertTrue("preset_hold($config) not confirmed: $reply", reply.toString().contains("preset_done"))
        } finally {
            conn.close()
        }
        Thread.sleep(500)
    }

    private fun publishLoadRequest() {
        val env = "msg(load_request,dispatch,ioport,cargoservice,load_request(none),${seq++})"
        mqtt.publish(TOPIC_LOAD, org.eclipse.paho.client.mqttv3.MqttMessage(env.toByteArray()))
    }

    private fun publishSonar(distanceCm: Number) {
        mqtt.publish(TOPIC_SONAR, org.eclipse.paho.client.mqttv3.MqttMessage(distanceCm.toString().toByteArray()))
    }

    private fun streamSonar(distanceCm: Number, seconds: Int) {
        repeat(seconds) { publishSonar(distanceCm); Thread.sleep(1000) }
    }

    private fun holdField(idx: Int): String {
        val parts = lastHold.split(",")
        return if (idx < parts.size) parts[idx] else ""
    }

    private fun occupiedCount(): Int = Regex("occupied").findAll(lastHold).count()

    private fun waitUntil(timeoutMs: Long, cond: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return true
            Thread.sleep(200)
        }
        return cond()
    }

    // ------------------------------------------------------------- TestPlans

    /** TP1.1 — load accepted on an empty hold (inherited from TP0.1). */
    @Test
    fun test1_loadAcceptedOnEmptyHold() {
        presetHold("empty")
        publishLoadRequest()
        assertTrue("the load request must be accepted (system engaged)",
            waitUntil(6000) { holdField(0) == "engaged" })

        streamSonar(5, 5)
        assertTrue("the accepted load must complete: disengaged with a slot occupied",
            waitUntil(25000) { holdField(0) == "disengaged" && lastHold.contains("occupied") })
    }

    /** TP1.2 — load refused when the hold is full (deferred from Sprint 0). */
    @Test
    fun test2_loadRefusedOnFullHold() {
        presetHold("full")
        publishLoadRequest()

        Thread.sleep(3000)
        assertFalse("a full hold must not become engaged", holdField(0) == "engaged")
        assertTrue("expected the hold to stay disengaged: $lastHold", holdField(0) == "disengaged")
        assertEquals("the four slots must still be occupied: $lastHold", 4, occupiedCount())
    }

    /** TP1.3 — retry later when the system is engaged: a racing second request is never queued. */
    @Test
    fun test3_retryLaterWhenEngaged() {
        presetHold("empty")
        publishLoadRequest()
        assertTrue("system should become engaged", waitUntil(6000) { holdField(0) == "engaged" })

        publishSonar(22)       // neutral mid-range: keeps the sonar quiet (resets both counters) so a
                               // far reading left by a previous plan cannot trip Out of service here
        publishLoadRequest()   // second request during the window
        Thread.sleep(1500)

        streamSonar(5, 5)      // complete the first operation
        assertTrue("only one slot must be occupied at the end (no second reservation)",
            waitUntil(25000) { holdField(0) == "disengaged" && occupiedCount() == 1 })
    }

    /** TP1.4 — engagement timeout discards the reservation (container accepted but never brought close). */
    @Test
    fun test4_engagementTimeoutDiscardsReservation() {
        presetHold("empty")
        publishLoadRequest()
        assertTrue("system should become engaged", waitUntil(6000) { holdField(0) == "engaged" })

        // On the Sprint 2 chain the logic sonar measures continuously during the window, and its
        // "no reading yet" default (Double.MAX_VALUE) already counts as FAR (> DFREE) -> that would
        // trip Out of service, not a silent timeout. To exercise the genuine 30 s window expiry
        // (customer accepted but never brings the container close enough), feed a MID-RANGE distance
        // DFREE/2 < d < DFREE (15 < 22 < 30): neither a detection nor a failure, so the window simply
        // expires and the reservation is discarded.
        streamSonar(22, 34)   // ~34 s of neutral readings; the 30 s window expires within it

        assertTrue("after the window the hold must be disengaged with NO slot occupied",
            waitUntil(3000) { holdField(0) == "disengaged" && !lastHold.contains("occupied") })
    }

    /** TP1.5 — complete load cycle (the same assertion as TP2.4 on the logic-sonar chain). */
    @Test
    fun test5_completeLoadCycle() {
        presetHold("empty")
        publishLoadRequest()
        assertTrue("system should become engaged", waitUntil(6000) { holdField(0) == "engaged" })

        streamSonar(5, 5) // sustained presence (< DFREE/2 = 15 cm) for a few seconds
        assertTrue("the full trip must complete: disengaged with exactly one slot occupied",
            waitUntil(25000) { holdField(0) == "disengaged" && occupiedCount() == 1 })
    }
}
