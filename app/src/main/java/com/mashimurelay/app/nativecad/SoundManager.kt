package com.mashimurelay.app.nativecad

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.view.SoundEffectConstants
import android.view.View

class SoundManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var isLoaded = false

    private var soundSiren = 0
    private var soundMotor = 0
    private var soundBuzzer = 0
    private var soundBell = 0
    private var soundLamp = 0

    private var streamSiren = 0
    private var streamMotor = 0
    private var streamBuzzer = 0
    private var streamBell = 0

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isBuzzerCycleActive = false
    private var isBuzzerToneOn = false

    // Anti-clipping debounce & state tracking for lamp chime
    private var lastLampChimeTimeMs: Long = 0L
    private val lampDebounceMs: Long = 35L
    private val prevLampStates = mutableMapOf<Int, Int>()

    private val buzzerCadenceRunnable = object : Runnable {
        override fun run() {
            if (!isBuzzerCycleActive || !isEnabled) return
            if (!isBuzzerToneOn) {
                // Fase BUNYI nyaring (1.0 detik)
                if (streamBuzzer != 0) soundPool?.stop(streamBuzzer)
                streamBuzzer = soundPool?.play(soundBuzzer, 1.0f, 1.0f, 2, -1, 1.0f) ?: 0
                isBuzzerToneOn = true
                mainHandler.postDelayed(this, 1000L)
            } else {
                // Fase HENING (0.8 detik jeda ritmik industri)
                if (streamBuzzer != 0) soundPool?.stop(streamBuzzer)
                streamBuzzer = 0
                isBuzzerToneOn = false
                mainHandler.postDelayed(this, 800L)
            }
        }
    }

    var isEnabled: Boolean = SettingsManager.isSoundEnabled(context)

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()

        soundPool?.let { sp ->
            try {
                soundSiren = loadAssetSound(sp, "sounds/siren.mp3")
                soundMotor = loadAssetSound(sp, "sounds/motor.mp3")
                soundBuzzer = loadAssetSound(sp, "sounds/buzzer.mp3")
                soundBell = loadAssetSound(sp, "sounds/bell.mp3")
                // Load Lamp Chime sound (autentik denting lampu SimuRelay)
                soundLamp = loadAssetSound(sp, "sounds/lamp.mp3").takeIf { it != 0 }
                    ?: loadAssetSound(sp, "sounds/click.mp3")
                isLoaded = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadAssetSound(sp: SoundPool, path: String): Int {
        return try {
            val afd = context.assets.openFd(path)
            sp.load(afd, 1)
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Memainkan suara chime / denting khas lampu SimuRelay (one-shot).
     * Dipanggil saat transisi naik (0 -> 1) dari lampu biasa maupun lampu kedip (lampBlink).
     */
    fun playLampChime() {
        if (!isEnabled || !isLoaded || soundLamp == 0) return
        val now = SystemClock.uptimeMillis()
        if (now - lastLampChimeTimeMs >= lampDebounceMs) {
            soundPool?.play(soundLamp, 0.85f, 0.85f, 1, 0, 1.0f)
            lastLampChimeTimeMs = now
        }
    }

    /**
     * Memainkan klik mekanis taktil native Android untuk sakelar.
     * Mencegah tercampurnya suara klik sakelar dengan denting lampu.
     */
    fun playSwitchClick(view: View? = null) {
        if (!isEnabled) return
        if (view != null) {
            view.playSoundEffect(SoundEffectConstants.CLICK)
        } else {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
        }
    }

    /**
     * Backward-compatible alias untuk panggilan switch click.
     */
    fun playClick(view: View? = null) {
        playSwitchClick(view)
    }

    fun updateSimulationSounds(
        elements: List<NativeElement>,
        elementFrames: Map<Int, Int>?,
        isSimulating: Boolean
    ) {
        if (!isEnabled || !isSimulating || elementFrames == null) {
            stopAllLoops()
            return
        }

        // 0. Lamp Chime untuk lampu biasa (lamp) dan lampu kedip (lampBlink) saat energize (0 -> 1)
        elements.forEach { el ->
            if (el.svgData == "lamp" || el.svgData == "lampBlink") {
                val frame = elementFrames[el.id] ?: 0
                val prev = prevLampStates[el.id] ?: 0
                if (frame == 1 && prev == 0) {
                    playLampChime()
                }
                prevLampStates[el.id] = frame
            }
        }

        // 1. Motor Running (category == "motors" and frame in 1..4)
        val hasActiveMotor = elements.any { el ->
            val def = NativeComponentRegistry.get(el.svgData)
            def?.category == "motors" && (elementFrames[el.id] ?: 0) in 1..4
        }
        if (hasActiveMotor) {
            if (streamMotor == 0 && soundMotor != 0) {
                streamMotor = soundPool?.play(soundMotor, 0.95f, 0.95f, 2, -1, 1.0f) ?: 0
            }
        } else {
            if (streamMotor != 0) {
                soundPool?.stop(streamMotor)
                streamMotor = 0
            }
        }

        // 2. Siren / Horn (svgData == "siren" and frame == 1, Continuous Uninterrupted Loop)
        val hasActiveSiren = elements.any { el ->
            el.svgData == "siren" && (elementFrames[el.id] ?: 0) == 1
        }
        if (hasActiveSiren) {
            if (streamSiren == 0 && soundSiren != 0) {
                streamSiren = soundPool?.play(soundSiren, 1.0f, 1.0f, 3, -1, 1.0f) ?: 0
            }
        } else {
            if (streamSiren != 0) {
                soundPool?.stop(streamSiren)
                streamSiren = 0
            }
        }

        // 3. Buzzer Ritmik Industri (svgData == "buzzer" and frame == 1, 1.0s bunyi / 0.8s hening)
        val hasActiveBuzzer = elements.any { el ->
            el.svgData == "buzzer" && (elementFrames[el.id] ?: 0) == 1
        }
        if (hasActiveBuzzer) {
            if (!isBuzzerCycleActive) {
                isBuzzerCycleActive = true
                isBuzzerToneOn = false
                mainHandler.post(buzzerCadenceRunnable)
            }
        } else {
            if (isBuzzerCycleActive) {
                isBuzzerCycleActive = false
                mainHandler.removeCallbacks(buzzerCadenceRunnable)
                if (streamBuzzer != 0) {
                    soundPool?.stop(streamBuzzer)
                    streamBuzzer = 0
                }
            }
        }

        // 4. Bell Ring Mekanis (svgData == "ring" and frame == 1, Murni tanpa fade distortion)
        val hasActiveBell = elements.any { el ->
            el.svgData == "ring" && (elementFrames[el.id] ?: 0) == 1
        }
        if (hasActiveBell) {
            if (streamBell == 0 && soundBell != 0) {
                streamBell = soundPool?.play(soundBell, 1.0f, 1.0f, 2, -1, 1.0f) ?: 0
            }
        } else {
            if (streamBell != 0) {
                soundPool?.stop(streamBell)
                streamBell = 0
            }
        }
    }

    fun stopAllLoops() {
        prevLampStates.clear()
        if (streamMotor != 0) {
            soundPool?.stop(streamMotor)
            streamMotor = 0
        }
        if (streamSiren != 0) {
            soundPool?.stop(streamSiren)
            streamSiren = 0
        }
        isBuzzerCycleActive = false
        mainHandler.removeCallbacks(buzzerCadenceRunnable)
        if (streamBuzzer != 0) {
            soundPool?.stop(streamBuzzer)
            streamBuzzer = 0
        }
        if (streamBell != 0) {
            soundPool?.stop(streamBell)
            streamBell = 0
        }
    }

    fun playFuseBlownSound() {
        if (!isEnabled) return
        try {
            val sampleRate = 44100
            val numSamples = (sampleRate * 0.08).toInt()
            val buffer = ShortArray(numSamples)
            val random = java.util.Random()
            for (i in 0 until numSamples) {
                val decay = kotlin.math.exp(-i.toDouble() / (sampleRate * 0.015))
                val noise = (random.nextDouble() * 2.0 - 1.0) * decay
                val osc = kotlin.math.sin(2.0 * Math.PI * (190.0 - i * 0.03) * i / sampleRate) * decay
                val mixed = (noise * 0.7 + osc * 0.3) * Short.MAX_VALUE
                buffer[i] = mixed.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            val track = android.media.AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                numSamples * 2,
                android.media.AudioTrack.MODE_STATIC
            )
            track.write(buffer, 0, numSamples)
            track.play()
            mainHandler.postDelayed({
                try {
                    track.stop()
                    track.release()
                } catch (ignored: Exception) {}
            }, 200L)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        stopAllLoops()
        soundPool?.release()
        soundPool = null
        isLoaded = false
    }
}
