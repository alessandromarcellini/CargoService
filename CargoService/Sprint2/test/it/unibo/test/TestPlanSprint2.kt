package it.unibo.test

import it.unibo.kactor.QakContext
import kotlinx.coroutines.runBlocking
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.junit.AfterClass
import org.junit.Assert.assertEquals
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
 * TestPlanSprint2 - automated TestPlans over the Sprint 2 MQTT push channel.
 *
 * PREREQUISITE: the Mosquitto broker MUST be running (tcp://localhost:1883):
 *   docker compose -f yamls/mosquitto.yml up -d
 * The test JVM starts the qak test configuration (cargoservicetest.pl) by itself.
 *
 * How the test plays the external nodes:
 *   - as the web GUI : publishes the load request as a qak envelope on load_requests,
 *                       and SUBSCRIBES to hold_state to observe the pushed state (the
 *                       Sprint 2 observation channel - no pull get_hold anymore);
 *   - as the device  : publishes raw distances on sonar_data, and SUBSCRIBES to
 *                       led_data to observe the LED commands;
 *   - as a test only : sets the Given via preset_hold over TCP (8030) request/reply.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TestPlanSprint2 {

    companion object {
        private const val HOST = "localhost"
        private const val TCP_PORT = 8030                 // ctxcargoservice (preset_hold)
        private const val BROKER = "tcp://localhost:1883"

        private const val TOPIC_LOAD = "load_requests"
        private const val TOPIC_HOLD = "hold_state"
        private const val TOPIC_SONAR = "sonar_data"
        private const val TOPIC_LED = "led_data"

        private lateinit var mqtt: MqttClient

        // latest holdstatus content seen on hold_state, e.g. "engaged,working,reserved,free,free,free,1"
        @Volatile
        private var lastHold: String = ""
        // observed LED commands, in order
        private val ledCmds = Collections.synchronizedList(ArrayList<String>())
        private var seq = 1

        @BeforeClass
        @JvmStatic
        fun startAll() {
            CommUtils.outcyan("TestPlanSprint2 | starting the qak test configuration (cargoservicetest.pl)")
            thread(isDaemon = true) {
                runBlocking {
                    QakContext.createContexts(HOST, this, "cargoservicetest.pl", "sysRules.pl", "ctxcargoservice")
                }
            }
            Thread.sleep(6000) // let the context server and the actors boot

            mqtt = MqttClient(BROKER, "testplan2_" + System.currentTimeMillis(), MemoryPersistence())
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

    private fun waitUntil(timeoutMs: Long, cond: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return true
            Thread.sleep(200)
        }
        return cond()
    }

    // ------------------------------------------------------------- TestPlans

    /** TP2.4 - detection through the logic sonar: full load cycle completes. */
    @Test
    fun test1_detectionFullCycle() {
        presetHold("empty")
        ledCmds.clear()
        publishLoadRequest()
        assertTrue("system should become engaged", waitUntil(6000) { holdField(0) == "engaged" })

        // sustained presence (< DFREE/2 = 15 cm) for a few seconds -> container_detected
        streamSonar(5, 5)

        // trip completes (mock robot confirms + 5 s marking pause) -> disengaged, one slot occupied
        assertTrue("cycle should end disengaged with a slot occupied",
            waitUntil(25000) { holdField(0) == "disengaged" && lastHold.contains("occupied") })
    }

    /** TP2.1 - LED contract: start_blinking at acceptance, stop_blinking at the exit. */
    @Test
    fun test2_ledContract() {
        presetHold("empty")
        ledCmds.clear()
        publishLoadRequest()
        assertTrue("start_blinking expected at acceptance", waitUntil(6000) { ledCmds.contains("start_blinking") })

        streamSonar(5, 5)
        assertTrue("stop_blinking expected at the exit", waitUntil(25000) { ledCmds.contains("stop_blinking") })
    }

    /** TP2.2 - sonar-driven Out of service during the window (terminal). */
    @Test
    fun test3_sonarOutOfService() {
        presetHold("empty")
        publishLoadRequest()
        assertTrue("system should become engaged", waitUntil(6000) { holdField(0) == "engaged" })

        // sustained failure (> DFREE = 30 cm) for a few seconds -> out_of_service
        streamSonar(120, 5)
        assertTrue("hold should turn out of service", waitUntil(10000) { holdField(0) == "outofservice" })

        // permanent: a further request stays out of service
        publishLoadRequest()
        assertTrue("still out of service after a new request",
            waitUntil(4000) { holdField(0) == "outofservice" })
    }

    /** TP2.3 - failure debounce: a transient far reading (<3 s) must NOT kill the system. */
    @Test
    fun test4_failureDebounce() {
        presetHold("empty")
        publishLoadRequest()
        assertTrue("system should become engaged", waitUntil(6000) { holdField(0) == "engaged" })

        // 2 s of failure (not sustained), then presence -> normal detection, no OOS
        streamSonar(120, 2)
        streamSonar(5, 5)
        assertTrue("cycle should complete normally (no out of service)",
            waitUntil(25000) { holdField(0) == "disengaged" && lastHold.contains("occupied") })
    }

    /** TP2.6 - boundary discipline: a request while engaged is never queued. */
    @Test
    fun test5_boundaryDisciplineWhileEngaged() {
        presetHold("empty")
        publishLoadRequest()
        assertTrue("system should become engaged", waitUntil(6000) { holdField(0) == "engaged" })

        // a second request during the window: must not create a second reservation
        publishLoadRequest()
        Thread.sleep(1500)

        // complete the first operation
        streamSonar(5, 5)
        assertTrue("only one slot must be occupied at the end",
            waitUntil(25000) {
                holdField(0) == "disengaged" &&
                    Regex("occupied").findAll(lastHold).count() == 1
            })
    }
}
