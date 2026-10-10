package com.openauto.dash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Dashwheel's Gemini is told it may do (DashAssistant). */
class AssistantToolsTest {

    private val functions = AssistantTools.declarations().getJSONObject(0).getJSONArray("functionDeclarations")
    private val names = (0 until functions.length()).map { functions.getJSONObject(it).getString("name") }

    @Test
    fun geminiCanReadEverythingAndAct() {
        listOf("get_everything", "do_action", "show_dashboard", "open_app", "navigate_to", "set_volume", "end_conversation")
            .forEach { assertTrue(it, it in names) }
        listOf("call_contact", "share_arrival", "add_stop", "start_parking_timer", "stop_parking_timer", "log_fuel_fill")
            .forEach { assertTrue(it, it in names) }
        // And search the web for the rest.
        assertTrue(AssistantTools.declarations().getJSONObject(1).has("googleSearch"))
    }

    @Test
    fun callsAndTextsWaitForTheDriversYes() {
        listOf("call_contact", "share_arrival").forEach { name ->
            val fn = functions.getJSONObject(names.indexOf(name))
            val params = fn.getJSONObject("parameters")
            assertTrue(name, params.getJSONObject("properties").getJSONObject("confirmed").getString("type") == "BOOLEAN")
            // Not required: Gemini first calls without it, to hear who matched.
            val required = params.getJSONArray("required")
            assertFalse(name, (0 until required.length()).any { required.getString(it) == "confirmed" })
            assertTrue(name, fn.getString("description").contains("yes"))
        }
    }

    @Test
    fun everyWheelActionButTheVoiceOnes() {
        val action = functions.getJSONObject(names.indexOf("do_action"))
        val offered = action.getJSONObject("parameters").getJSONObject("properties").getJSONObject("action").getJSONArray("enum")
        val list = (0 until offered.length()).map { offered.getString(it) }
        assertTrue("MEDIA_NEXT" in list && "NEXT_DASHBOARD" in list && "NAVIGATE_HOME" in list)
        // Those would take the microphone from the conversation.
        assertFalse("GEMINI_LIVE" in list || "ASK_MECHANIC" in list || "VOICE_ASSISTANT" in list)
    }
}
