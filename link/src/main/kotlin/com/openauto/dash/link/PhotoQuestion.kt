package com.openauto.dash.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * A photo for the car's AI to read, taken on the phone: a warning light lit
 * on the dashboard ("what's this light?"), or a page of the service booklet
 * (its intervals, and the services stamped in it). The car asks Gemini with
 * its own key, so the phone needs none, and answers here; the photo itself
 * is kept nowhere.
 */

/** Phone → head unit: [jpeg] (base64) to read as [kind]. Answered by [PhotoAnswer] under the same [id]. */
@Serializable
@SerialName("photo_question")
data class PhotoQuestion(val id: Long, val kind: String, val jpeg: String) : LinkMessage {
    companion object {
        const val LIGHT = "light"
        const val BOOKLET = "booklet"

        /** The photo's bytes at most, before base64: well inside a frame once a third bigger. */
        const val MAX_BYTES = 300 * 1024
    }
}

/**
 * Head unit → phone: what the AI made of the photo. For a light: [title]
 * (what it is), [severity] ([SEVERITY_OK], [SEVERITY_SOON], [SEVERITY_STOP]),
 * [text] (what it means) and [steps] (what to do). For a booklet page:
 * [services], each to check before it goes to the car. [error] says why
 * there's no answer (no key, offline, nothing readable); [ok] false then.
 */
@Serializable
@SerialName("photo_answer")
data class PhotoAnswer(
    val id: Long,
    val kind: String,
    val ok: Boolean,
    val title: String = "",
    val severity: String = "",
    val text: String = "",
    val steps: List<String> = emptyList(),
    val services: List<ServiceEntry> = emptyList(),
    val error: String = ""
) : LinkMessage {
    companion object {
        const val SEVERITY_OK = "ok"
        const val SEVERITY_SOON = "soon"
        const val SEVERITY_STOP = "stop"
    }
}

/**
 * One line of the service booklet: an item of the car's servicing planner
 * ([kind], the planner's own names: OIL, TIMING_BELT...) with how often it
 * is due and/or when it was last done ([doneDate] yyyy-MM-dd, [doneKm]).
 * [label] is the item in the driver's language, for the phone to show.
 */
@Serializable
data class ServiceEntry(
    val kind: String,
    val label: String = "",
    val everyKm: Int? = null,
    val everyMonths: Int? = null,
    val doneDate: String? = null,
    val doneKm: Int? = null
)

/** Phone → head unit: the booklet's lines the driver checked, for the servicing planner. */
@Serializable
@SerialName("service_entries")
data class ServiceEntries(val entries: List<ServiceEntry>) : LinkMessage
