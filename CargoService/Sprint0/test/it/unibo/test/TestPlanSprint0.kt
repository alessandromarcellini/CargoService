package it.unibo.test

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import unibo.basicomm23.interfaces.Interaction
import unibo.basicomm23.tcp.TcpClientSupport
import unibo.basicomm23.utils.CommUtils

/**
 * TestPlanSprint0 — automated system test for the cargoservice Sprint 0 model.
 *
 * Scope (see the "Test plans" section of sprint0_v1.html):
 * Sprint 0 only fixes the OBSERVABLE behaviour of cargoservice in reply to a
 * load_request. Of the three modelled answers only the happy path
 * (empty hold, DISENGAGED -> load_accepted) has a Given that coincides with the
 * natural startup state, so it is the only case testable black-box here.
 *
 * TP0.1  Given cargoservice started, hold empty (slots 1..4 free), DISENGAGED
 *        When  a single load_request(none) is sent on port 8020
 *        Then  the reply is load_accepted(SlotId) with SlotId in {1,2,3,4}
 *
 * The service under test must be running first (ctxcargoservice @ localhost:8020).
 *
 * The other two branches (load_refused on a full hold, load_retrylater on an
 * engaged hold) are documented as TP0.2 / TP0.3 and deferred to Sprint 1, when
 * an observability/controllability hook lets a test preset the hold state.
 */
class TestPlanSprint0 {

    private val HOST = "localhost"
    private val PORT = 8020            // ctxcargoservice, from cargoservice_sprint0.qak
    private var conn: Interaction? = null

    @Before
    fun setup() {
        CommUtils.outcyan("Connecting to cargoservice ($HOST:$PORT)")
        try {
            conn = TcpClientSupport.connect(HOST, PORT, 10)
        } catch (e: Exception) {
            fail("TCP connection error: make sure the cargoservice server is running on $HOST:$PORT. ${e.message}")
        }
    }

    @After
    fun teardown() {
        conn?.close()
        CommUtils.outcyan("Connection closed")
    }

    /**
     * TP0.1 — happy path. Asserts the SPECIFIC expected reply, not merely
     * "one of the three replies", so a wrong branch turns the test red.
     */
    @Test
    fun testLoadAcceptedOnEmptyHold() {
        CommUtils.outmagenta("TP0.1: send load_request on an empty hold, expect load_accepted(slot 1..4)")
        try {
            // request envelope built from the model:
            // msg(msgId, msgType, sender, receiver, content, seqNumber)
            val requestMsg =
                "msg(load_request,request,testunit,cargoservice,load_request(none),1)"

            val reply = conn!!.request(requestMsg)
            CommUtils.outgreen("Reply from cargoservice: $reply")
            assertNotNull("No reply received from cargoservice", reply)

            val body = reply.toString()

            // Then: must be a load_accepted, and NOT a refused/retrylater
            assertTrue(
                "Expected load_accepted but got: $body",
                body.contains("load_accepted")
            )
            assertTrue(
                "Unexpected extra/other reply mixed in: $body",
                !body.contains("load_refused") && !body.contains("load_retrylater")
            )

            // And the reserved slot id must be one of the four storage slots 1..4
            val slotId = extractSlotId(body)
            assertNotNull("Could not read the reserved slot id from: $body", slotId)
            assertTrue(
                "Reserved slot id out of range (expected 1..4), got: $slotId",
                slotId!! in 1..4
            )
        } catch (e: Exception) {
            fail("Test failed during execution: ${e.message}")
        }
    }

    /** Extracts the integer argument of load_accepted(<id>) from the reply text. */
    private fun extractSlotId(reply: String): Int? {
        val m = Regex("""load_accepted\(\s*(\d+)\s*\)""").find(reply) ?: return null
        return m.groupValues[1].toIntOrNull()
    }
}
