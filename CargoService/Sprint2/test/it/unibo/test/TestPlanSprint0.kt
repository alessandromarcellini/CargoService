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
 * TestPlanSprint0 (Sprint 2 regression) — the Sprint 0 plan set, re-run on the
 * Sprint 2 MQTT push architecture.
 *
 * Sprint 0 specified the three answers to a load_request: accepted, refused
 * (full hold) and retrylater (engaged). Here they are re-verified end-to-end on
 * the current chain: the boundary request is PUBLISHED on `load_requests`, the
 * Given is set with `preset_hold` over TCP, and the outcome is READ from the
 * pushed `hold_state` feed (no pull get_hold anymore) — exactly like
 * TestPlanSprint2.
 *
 * PREREQUISITE: the Mosquitto broker MUST be running (tcp://localhost:1883).
 * build.gradle runs the test task with forkEvery = 1, so this class boots its
 * own copy of the qak test configuration in its own JVM (TCP 8030) and never
 * clashes with the other TestPlan classes.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TestPlanSprint0 {

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
            CommUtils.outcyan("TestPlanSprint0 (regression) | starting the qak test configuration (cargoservicetest.pl)")
            thread(isDaemon = true) {
                runBlocking {
                    QakContext.createContexts(HOST, this, "cargoservicetest.pl", "sysRules.pl", "ctxcargoservice")
                }
            }
            Thread.sleep(6000) // let the context server and the actors boot

            mqtt = MqttClient(BROKER, "testplan0_" + System.currentTimeMillis(), MemoryPersistence())
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

    /** preset_hold over TCP (the only surviving test-support request/reply). */
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

    /** publish the given distance once per second for [seconds] seconds. */
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

    /** TP0.1 — load accepted on an empty hold: the request is honoured (engaged)
        and the reserved load completes the full trip (one slot occupied). */
    @Test
    fun test1_loadAcceptedOnEmptyHold() {
        presetHold("empty")
        ledCmds.clear()
        publishLoadRequest()
        assertTrue("the load request must be accepted (system engaged)",
            waitUntil(6000) { holdField(0) == "engaged" })

        streamSonar(5, 5) // sustained presence (< DFREE/2 = 15 cm) -> container_detected
        assertTrue("the accepted load must complete: disengaged with a slot occupied",
            waitUntil(25000) { holdField(0) == "disengaged" && lastHold.contains("occupied") })
    }

    /** TP0.2 — load refused when the hold is full: no engagement, all four slots stay occupied. */
    @Test
    fun test2_loadRefusedOnFullHold() {
        presetHold("full")
        publishLoadRequest()

        // a full hold must NOT engage; the state stays disengaged with the four slots occupied
        Thread.sleep(3000)
        assertFalse("a full hold must not become engaged", holdField(0) == "engaged")
        assertTrue("expected the hold to stay disengaged: $lastHold", holdField(0) == "disengaged")
        assertEquals("the four slots must still be occupied: $lastHold", 4, occupiedCount())
    }

    /** TP0.3 — retry later when the system is engaged: a second request never
        creates a second reservation (the busy service refuses to queue it). */
    @Test
    fun test3_retryLaterWhenEngaged() {
        presetHold("empty")
        publishLoadRequest()
        assertTrue("system should become engaged", waitUntil(6000) { holdField(0) == "engaged" })

        publishSonar(22)   // neutral mid-range: keeps the sonar quiet (resets both counters) so a far
                           // reading left by a previous plan cannot trip Out of service in this window

        // a second request during the window must not be queued (retrylater semantics)
        publishLoadRequest()
        Thread.sleep(1500)

        streamSonar(5, 5) // complete the FIRST operation
        assertTrue("only one slot must be occupied at the end (no second reservation)",
            waitUntil(25000) { holdField(0) == "disengaged" && occupiedCount() == 1 })
    }
}
