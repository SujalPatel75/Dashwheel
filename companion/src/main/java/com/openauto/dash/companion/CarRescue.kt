package com.openauto.dash.companion

import android.os.Handler
import android.os.Looper
import com.openauto.dash.link.RescueCommand
import com.openauto.dash.link.RescueResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Ways out for a driver locked out of the car's screen ([RescueCommand]),
 * sent from the Rescue card and run on the car with no tap there. The car
 * answers each with a [RescueResult]; no answer means a Dashwheel too old
 * to know them.
 */
object CarRescue {
    private const val ANSWER_MS = 6_000L

    enum class Status { SENDING, DONE, FAILED, NO_ANSWER }

    /** The latest action sent and what became of it. */
    data class Answer(val action: RescueCommand.Action, val status: Status)

    private val _answer = MutableStateFlow<Answer?>(null)
    val answer: StateFlow<Answer?> = _answer

    private val ids = AtomicLong(0)
    @Volatile private var awaiting: Pair<Long, RescueCommand.Action>? = null
    private val main = Handler(Looper.getMainLooper())
    private val noAnswer = Runnable {
        awaiting?.let { (_, action) ->
            awaiting = null
            _answer.value = Answer(action, Status.NO_ANSWER)
        }
    }

    /** Sends [action] to the car; false with no car linked. */
    fun send(action: RescueCommand.Action): Boolean {
        if (LinkServer.state.value !is LinkState.Connected) return false
        val id = ids.incrementAndGet()
        awaiting = id to action
        _answer.value = Answer(action, Status.SENDING)
        LinkServer.send(RescueCommand(action, id))
        main.removeCallbacks(noAnswer)
        main.postDelayed(noAnswer, ANSWER_MS)
        return true
    }

    fun answered(result: RescueResult) {
        main.post {
            val (id, action) = awaiting ?: return@post
            if (id != result.id) return@post
            awaiting = null
            main.removeCallbacks(noAnswer)
            _answer.value = Answer(action, if (result.ok) Status.DONE else Status.FAILED)
        }
    }
}
