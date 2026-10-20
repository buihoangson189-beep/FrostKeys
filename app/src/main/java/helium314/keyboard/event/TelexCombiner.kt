// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import java.util.ArrayList

/**
 * Vietnamese Telex combiner.
 * Keeps the word being typed in [composing] (shown as composing text through [combiningStateFeedback]),
 * and runs every letter through [TelexEngine]. Any non-letter key commits the word first.
 */
class TelexCombiner : Combiner {

    private val composing = StringBuilder()

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        if (event.keyCode == KeyCode.SHIFT) return event

        if (event.isFunctionalKeyEvent) {
            if (event.keyCode == KeyCode.DELETE && composing.isNotEmpty()) {
                val last = composing.codePointBefore(composing.length)
                composing.setLength(composing.length - Character.charCount(last))
                return Event.createConsumedEvent(event)
            }
            return flush(event)
        }

        val cp = event.codePoint
        if (cp > 0 && cp < 0x10000 && Character.isLetter(cp)) {
            val next = TelexEngine.type(composing.toString(), cp.toChar())
            composing.setLength(0)
            composing.append(next)
            return Event.createConsumedEvent(event)
        }
        // space, digits, punctuation, emoji...: commit the word, then let the key through
        return flush(event)
    }

    private fun flush(event: Event): Event {
        val text = composing.toString()
        reset()
        return if (text.isEmpty()) event
        else Event.createSoftwareTextEvent(text, KeyCode.MULTIPLE_CODE_POINTS, event)
    }

    override val combiningStateFeedback: CharSequence
        get() = composing.toString()

    override fun reset() {
        composing.setLength(0)
    }
}
