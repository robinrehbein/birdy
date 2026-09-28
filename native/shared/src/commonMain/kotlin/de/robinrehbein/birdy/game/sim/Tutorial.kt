package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.GameEvent
import de.robinrehbein.birdy.game.GameSimulation
import de.robinrehbein.birdy.game.HandMode
import de.robinrehbein.birdy.game.TutorialStep
import kotlin.math.sin

/**
 * The first-run tutorial part of `updatePlaying` (main.js:1624-1651, main-b.md §9).
 * Returns true while the bird is frozen in front of the blocked row (rest of the frame skipped).
 */
internal fun GameSimulation.updateTutorial(): Boolean {
    val s = state
    if (!s.tutorialActive) return false
    val gate = tutorialGate
    if (s.tutorialStep == TutorialStep.Fly && gate != null && gate.z > -13) {
        if (gate.lanes[s.lane].blocked) {
            s.tutorialStep = TutorialStep.Switch
            s.freezeY = s.y
            s.hand = HandMode.Side
            // From the dodge lesson on, the tap boundaries are shown as in normal runs.
            showZones(restart = false)
        } else {
            s.tutorialStep = TutorialStep.Go // already dodged on their own
        }
    }
    if (s.tutorialStep == TutorialStep.Switch) {
        // Frozen in front of the blocked row; the bird just hovers.
        s.y = s.freezeY + sin(s.time * 3) * 0.15
        s.vy = 0.0
        return true
    }
    if (s.tutorialStep == TutorialStep.Go && gate != null && gate.passed) {
        s.tutorialActive = false
        s.tutorialStep = null
        progress.setTutorialDone()
        emit(GameEvent.Toast("tutDone"))
        emit(GameEvent.Fanfare())
        showZones(restart = false)
    }
    return false
}
