package demo.AnnotationSystem.Utilities

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper


class BeepHelper
{
    private val toneG = ToneGenerator(AudioManager.STREAM_ACCESSIBILITY, 100)

    fun beep() {
        toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            toneG.release()
        }, 200L)
    }

    fun endBeep() {
        toneG.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            toneG.release()
        }, 350L)
    }
}