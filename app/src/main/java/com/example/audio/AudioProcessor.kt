package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

class AudioProcessor {
    private val TAG = "AudioProcessor"

    // Configuration
    var sampleRate = 44100
    var bufferSize = 1024
    var isInputActive = false
    var selectedMicInput = com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.SYSTEM_DEFAULT
    private var activeAudioRecord: android.media.AudioRecord? = null
    private var appContext: android.content.Context? = null
    
    // Volume controls
    var volInput = 0.8f
    var volVocal = 1.0f
    var volEffect = 0.5f
    var volMaster = 0.8f

    // Selected device profile info
    var latencyMs = MutableStateFlow(12)
    var cpuUsagePercent = MutableStateFlow(4)

    // Current State for DSP (12 racks reference)
    var effectsState = AllEffectsState()

    // Spectrum Analyzer & VU Meter Live State Flows
    private val _spectrumData = MutableStateFlow(FloatArray(32) { 0.05f })
    val spectrumData: StateFlow<FloatArray> = _spectrumData

    private val _vuLevels = MutableStateFlow(Pair(0.02f, 0.02f)) // Left, Right RMS normalized (0.0f to 1.0f)
    val vuLevels: StateFlow<Pair<Float, Float>> = _vuLevels

    private val _vuPeak = MutableStateFlow(Pair(0.02f, 0.02f)) // Peak levels hold
    val vuPeak: StateFlow<Pair<Float, Float>> = _vuPeak

    // Audio Thread
    private var audioJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // Delay Line Memory
    private var delayBufferL = FloatArray(44100 * 2) { 0f }
    private var delayBufferR = FloatArray(44100 * 2) { 0f }
    private var delayWritePos = 0

    // Reverb Line Memory (Schroeder Comb Filters + Allpass)
    private val combDelays = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val combBuffersL = List(8) { FloatArray(2000) }
    private val combBuffersR = List(8) { FloatArray(2000) }
    private val combIndex = IntArray(8) { 0 }
    private val combFeedback = FloatArray(8) { 0.82f }

    private val allpassDelays = intArrayOf(225, 341, 411, 513)
    private val allpassBuffersL = List(4) { FloatArray(600) }
    private val allpassBuffersR = List(4) { FloatArray(600) }
    private val allpassIndex = IntArray(4) { 0 }
    
    // Synth state for demo vocalist singer
    private var synthPhase = 0.0
    private var voicePitchHz = 220.0
    private var targetPitchHz = 220.0
    private var melodyTimer = 0
    private var melodyNoteIdx = 0
    private var vibratoPhase = 0.0

    // List of notes inside demo vocal track (Indonesian Pentatonic Scale loop)
    private val demoMelodyNotes = doubleArrayOf(
        220.00, 246.94, 277.18, 293.66, 329.63, 369.99, // A Major/Pentatonic fundamental octave 1
        293.66, 329.63, 369.99, 440.00, 493.88, 554.37, // Octave 2
        440.00, 369.99, 329.63, 293.66, 277.18, 220.00
    )

    fun start(context: android.content.Context) {
        if (audioJob != null) return
        appContext = context.applicationContext
        audioJob = scope.launch {
            runAudioLoop()
        }
    }

    fun stop() {
        audioJob?.cancel()
        audioJob = null
        activeAudioRecord = null
    }

    fun updateActivePreferredDevice(context: android.content.Context) {
        val record = activeAudioRecord ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
            
            val deviceType = when (selectedMicInput) {
                com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.INTERNAL_MIC -> android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC
                com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.HEADSET_MIC -> android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
                com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.BLUETOOTH_MIC -> android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.USB_MIC -> android.media.AudioDeviceInfo.TYPE_USB_DEVICE
                com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.SYSTEM_DEFAULT -> null
            }
            
            if (deviceType == null) {
                record.setPreferredDevice(null)
                Log.d(TAG, "Dynamic routing change: Default/System Mic")
            } else {
                val matchingDevice = devices.find { it.type == deviceType }
                    ?: if (selectedMicInput == com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.USB_MIC) {
                        devices.find { it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET }
                    } else if (selectedMicInput == com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.BLUETOOTH_MIC) {
                        devices.find { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
                    } else null
                
                if (matchingDevice != null) {
                    val success = record.setPreferredDevice(matchingDevice)
                    Log.d(TAG, "Dynamic routing change to: ${matchingDevice.productName} (Type: ${matchingDevice.type}), Success: $success")
                } else {
                    record.setPreferredDevice(null)
                    Log.d(TAG, "Microphone device type $deviceType not physically connected. Set preferred device to null.")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun runAudioLoop() {
        var audioRecord: AudioRecord? = null
        var audioTrack: AudioTrack? = null

        // Calculate minimum buffer size
        val minRecordBufSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val minTrackBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val actualRecordBufSize = max(minRecordBufSize, bufferSize * 2)
        val actualTrackBufSize = max(minTrackBufSize, bufferSize * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                actualRecordBufSize
            )

            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording()
                activeAudioRecord = audioRecord
                appContext?.let { updateActivePreferredDevice(it) }
                Log.d(TAG, "AudioRecord initialized and recording started.")
            } else {
                Log.e(TAG, "Failed to initialize AudioRecord.")
                audioRecord.release()
                audioRecord = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord initialization error: ${e.message}. Fallback mode active.")
            audioRecord = null
        }

        try {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                actualTrackBufSize,
                AudioTrack.MODE_STREAM
            )
            audioTrack.play()
            Log.d(TAG, "AudioTrack initialized and playing.")
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack initialization error: ${e.message}")
            audioRecord?.release()
            return
        }

        val inBuf = ShortArray(bufferSize)
        val outBuf = ShortArray(bufferSize * 2) // Stereo output

        var lastSpectrumTime = System.currentTimeMillis()
        val spectrumHistory = Array(5) { FloatArray(32) }
        var historyIndex = 0

        var peakL = 0.05f
        var peakR = 0.05f

        while (audioJob?.isActive == true) {
            val startTime = System.nanoTime()

            // 1. READ MICROPHONE INPUT
            var samplesRead = 0
            if (isInputActive && audioRecord != null) {
                samplesRead = audioRecord.read(inBuf, 0, bufferSize)
            }

            // Fill with silence if no real samples read
            if (samplesRead <= 0) {
                for (i in 0 until bufferSize) {
                    inBuf[i] = 0
                }
            }

            // 2. DIGITALLY MONITORED REAL MICROPHONE AUDIO WITH 12-RACK DSP PROCESSING ENGINE

            // Calculate DSP Cpu Time
            val dspStart = System.nanoTime()

            // 3. PROCESS WITH THE 12-RACK SYSTEM IF AUDIO PROCESSING IS ON
            var rmsLeftSum = 0.0
            var rmsRightSum = 0.0

            val tempSpectrum = FloatArray(32) { 0.02f }

            for (i in 0 until bufferSize) {
                // Normalize input short sample to float [-1.0f, 1.0f]
                val rawVal = inBuf[i] / 32768.1f
                var sample = rawVal * volInput

                // Apply DSP Elements in sequence:
                
                // RACK 10: NOISE REDUCTION / GATE
                if (effectsState.noiseReduction.isEnabled && !effectsState.noiseReduction.isBypassed) {
                    val thresholdVal = 10.0.pow(effectsState.noiseReduction.noiseThresholdDb / 20.0).toFloat()
                    if (abs(sample) < thresholdVal) {
                        // Background Sound Reduction Soft Gate
                        sample *= 0.15f
                    } else {
                        // Reduce hiss and hum
                        val dampHiss = 1.0f - (effectsState.noiseReduction.hissReductionPercent / 100f * 0.5f)
                        val dampHum = 1.0f - (effectsState.noiseReduction.hissReductionPercent / 100f * 0.3f)
                        sample *= dampHiss * dampHum
                    }
                }

                // RACK 9: DE-ESSER (Compress high frequencies)
                if (effectsState.deEsser.isEnabled && !effectsState.deEsser.isBypassed) {
                    // Check if high frequency "S" exists
                    val deEssThresh = 10.0.pow(effectsState.deEsser.thresholdDb / 20.0).toFloat()
                    if (abs(sample) > deEssThresh) {
                        val reduction = 1.0f - (effectsState.deEsser.amountPercent / 100.0f * 0.6f)
                        sample *= reduction
                    }
                }

                // RACK 7: PITCH CORRECTION (Slight robot simulation or retuning resonance based on Key & Scale settings)
                if (effectsState.pitchCorrection.isEnabled && !effectsState.pitchCorrection.isBypassed) {
                    val retuneSpd = effectsState.pitchCorrection.retuneSpeedPercent / 100f
                    val flexTune = effectsState.pitchCorrection.flexTunePercent / 100f
                    if (effectsState.pitchCorrection.mode == PitchMode.HARD_TUNE || effectsState.pitchCorrection.mode == PitchMode.ROBOT) {
                        // Apply hard staircase waveshaping for classic AutoTune robotics
                        val scaleDegreeFactor = sin(sample * Math.PI)
                        sample = (sample * 0.4f + scaleDegreeFactor.toFloat() * 0.6f) * (1.1f * retuneSpd)
                    } else {
                        // Natural tuning resonance (subtle soft compression around the vocal registers)
                        sample = sample * (1f - 0.15f * flexTune) + sin(sample * 1.25f).toFloat() * 0.08f * flexTune
                    }
                }

                // RACK 6: HARMONY ENGINE (Simulates secondary voices tuned to beautiful intervals)
                var harmonyL = 0.0f
                var harmonyR = 0.0f
                if (effectsState.harmony.isEnabled && !effectsState.harmony.isBypassed) {
                    val scale = effectsState.harmony.pitchShiftSemitones
                    val voiceMultiplier = effectsState.harmony.voicesCount
                    val humanize = effectsState.harmony.humanizePercent / 100.0f
                    val width = effectsState.harmony.stereoWidth / 100.0f

                    // Voice 1 (+4 semitones - Major Third)
                    val shiftPhase1 = sin(synthPhase * 1.259) // 2^(4/12) is ~1.2599
                    // Voice 2 (+7 semitones - Perfect Fifth)
                    val shiftPhase2 = sin(synthPhase * 1.498) // 2^(7/12) is ~1.498
                    
                    val voice1 = shiftPhase1.toFloat() * 0.45f * sample
                    val voice2 = shiftPhase2.toFloat() * 0.35f * sample

                    if (voiceMultiplier >= 3) {
                        // Substantial harmony pan wide
                        harmonyL = (voice1 * (1.0f + width) + voice2 * (1.0f - width)) * (1.0f - humanize * 0.2f)
                        harmonyR = (voice2 * (1.0f + width) + voice1 * (1.0f - width)) * (1.0f + humanize * 0.3f)
                    } else {
                        harmonyL = voice1 * 0.7f
                        harmonyR = voice1 * 0.7f
                    }
                }

                // RACK 1: PARAMETRIC EQ (Real-time graphic EQ bands mapping)
                if (effectsState.eq.isEnabled && !effectsState.eq.isBypassed) {
                    // Simulating EQ bands by multiplying bands' gain to active frequencies
                    // We map lower index bands to lower pitch, higher to higher
                    val eqFactor = 1.0f + (effectsState.eq.bands[15] / 12f * 0.5f) // Mid range band
                    val bassFactor = 1.0f + (effectsState.eq.bands[4] / 12f * 0.6f) // Bass band
                    val airFactor = 1.0f + (effectsState.eq.bands[28] / 12f * 0.8f) // Air/High band
                    
                    sample = sample * eqFactor
                    // Simple shelving approximation
                    if (i % 2 == 0) sample *= bassFactor else sample *= airFactor

                    // Apply high pass and low pass simulation
                    if (effectsState.eq.highPassHz > 120) {
                        sample *= 0.88f // Dampen bass
                    }
                    if (effectsState.eq.lowPassHz < 10000) {
                        sample *= 0.92f // Dampen highs
                    }
                }

                // RACK 2: COMPRESSOR
                if (effectsState.compressor.isEnabled && !effectsState.compressor.isBypassed) {
                    val thresholdVal = 10.0.pow(effectsState.compressor.thresholdDb / 20.0).toFloat()
                    val ratio = effectsState.compressor.ratio
                    val makeup = 10.0.pow(effectsState.compressor.makeupGainDb / 20.0).toFloat()

                    if (abs(sample) > thresholdVal) {
                        val excess = abs(sample) - thresholdVal
                        val compressionFactor = 1.0f / ratio
                        sample = (thresholdVal + excess * compressionFactor) * sign(sample)
                    }
                    sample *= makeup
                }

                // RACK 8: VOCAL ENHANCER
                if (effectsState.enhancer.isEnabled && !effectsState.enhancer.isBypassed) {
                    val warmth = effectsState.enhancer.warmth / 100.0f
                    val clarity = effectsState.enhancer.clarity / 100.0f
                    val air = effectsState.enhancer.air / 100.0f

                    // Analog Saturation adds warmth
                    sample = (sample * (1.0f - warmth * 0.3f) + sin(sample * 1.51f).toFloat() * 0.2f * warmth)
                    // High shelf boost for clarity and air
                    if (i % 3 == 0) {
                        sample *= (1.0f + (clarity * 0.35f) + (air * 0.45f))
                    }
                }

                // RACK 12: EXCITER (Analog simulation)
                if (effectsState.exciter.isEnabled && !effectsState.exciter.isBypassed) {
                    val tube = effectsState.exciter.tubeSaturationPercent / 100.0f
                    val bright = effectsState.exciter.brightFactor / 100.0f
                    // Non-linear wave transformation
                    val satSample = (1.5f * sample - 0.5f * sample * sample * sample)
                    sample = sample * (1f - tube * 0.4f) + satSample * tube * 0.4f
                    if (i % 4 == 0) sample *= (1.0f + bright * 0.3f)
                }

                // SPLIT TO STEREO STREAM
                var outL = sample * volVocal + harmonyL
                var outR = sample * volVocal + harmonyR

                // RACK 11: STEREO IMAGER
                if (effectsState.stereoImager.isEnabled && !effectsState.stereoImager.isBypassed) {
                    val width = effectsState.stereoImager.widthPercent / 100.0f
                    val mid = (outL + outR) * 0.5f
                    val side = (outL - outR) * 0.5f
                    outL = (mid + side * width).coerceIn(-1.0f, 1.0f)
                    outR = (mid - side * width).coerceIn(-1.0f, 1.0f)
                }

                // RACK 5: DELAY PROFESSIONAL (ECHO)
                var delayOutL = 0f
                var delayOutR = 0f
                if (effectsState.delay.isEnabled && !effectsState.delay.isBypassed) {
                    val dTimeSps = ((effectsState.delay.timeMs / 1000f) * sampleRate).toInt().coerceIn(100, delayBufferL.size - 1)
                    val feedback = effectsState.delay.feedbackPercent / 100f
                    val mix = effectsState.delay.mixPercent / 100f
                    val pingPong = effectsState.delay.isPingPong

                    val readPos = (delayWritePos - dTimeSps + delayBufferL.size) % delayBufferL.size
                    
                    delayOutL = delayBufferL[readPos]
                    delayOutR = delayBufferR[readPos]

                    if (pingPong) {
                        // Swap channels for bouncing feedback
                        delayBufferL[delayWritePos] = outL + delayOutR * feedback
                        delayBufferR[delayWritePos] = outR + delayOutL * feedback
                    } else {
                        delayBufferL[delayWritePos] = outL + delayOutL * feedback
                        delayBufferR[delayWritePos] = outR + delayOutR * feedback
                    }

                    outL = outL * (1f - mix) + delayOutL * mix
                    outR = outR * (1f - mix) + delayOutR * mix
                }

                // UPDATE DELAY WRITE POINTER
                delayWritePos = (delayWritePos + 1) % delayBufferL.size

                // RACK 4: REVERB (Schroeder reverberator network simulation)
                if (effectsState.reverb.isEnabled && !effectsState.reverb.isBypassed) {
                    val mix = effectsState.reverb.mixPercent / 100.0f
                    val decay = effectsState.reverb.decaySec / 3f // Map decay
                    val width = effectsState.reverb.width / 100.0f

                    // Sum comb filters
                    var combSumL = 0f
                    var combSumR = 0f
                    for (c in 0..7) {
                        val len = combDelays[c]
                        val bufL = combBuffersL[c]
                        val bufR = combBuffersR[c]
                        val idx = combIndex[c]

                        val outLComb = bufL[idx]
                        val outRComb = bufR[idx]

                        combSumL += outLComb
                        combSumR += outRComb

                        // Feedback loop
                        val dampingFactor = effectsState.reverb.damping / 100f * 0.45f
                        bufL[idx] = outL + outLComb * (combFeedback[c] * decay * (1f - dampingFactor))
                        bufR[idx] = outR + outRComb * (combFeedback[c] * decay * (1f - dampingFactor))

                        combIndex[c] = (idx + 1) % len
                    }

                    // Feed through simple allpass structures
                    var apL = combSumL * 0.125f
                    var apR = combSumR * 0.125f
                    for (a in 0..3) {
                        val len = allpassDelays[a]
                        val bufL = allpassBuffersL[a]
                        val bufR = allpassBuffersR[a]
                        val idx = allpassIndex[a]

                        val delayOutLAll = bufL[idx]
                        val delayOutRAll = bufR[idx]

                        val feedL = apL + delayOutLAll * 0.5f
                        val feedR = apR + delayOutRAll * 0.5f

                        bufL[idx] = feedL
                        bufR[idx] = feedR

                        apL = (-0.5f * feedL) + delayOutLAll
                        apR = (-0.5f * feedR) + delayOutRAll

                        allpassIndex[a] = (idx + 1) % len
                    }

                    val wetL = apL * (1.0f + width)
                    val wetR = apR * (1.0f + width)

                    outL = outL * (1f - mix) + wetL * mix * volEffect
                    outR = outR * (1f - mix) + wetR * mix * volEffect
                }

                // RACK 3: LIMITER (Apply mastering ceiling check)
                if (effectsState.limiter.isEnabled && !effectsState.limiter.isBypassed) {
                    val ceil = 10.0.pow(effectsState.limiter.ceilingDb / 20.0).toFloat()
                    outL = outL.coerceIn(-ceil, ceil)
                    outR = outR.coerceIn(-ceil, ceil)
                }

                // Apply master volume controls representation
                val finalL = (outL * volMaster).coerceIn(-1.0f, 1.0f)
                val finalR = (outR * volMaster).coerceIn(-1.0f, 1.0f)

                // Accumulate RMS
                rmsLeftSum += finalL * finalL
                rmsRightSum += finalR * finalR

                // Convert 1.0f limit back to Short [-32768, 32767]
                outBuf[i * 2] = (finalL * 32767f).toInt().toShort()
                outBuf[i * 2 + 1] = (finalR * 32767f).toInt().toShort()

                // Simulating Realtime Spectrum bar values
                // Map frequency energies to 32 bands
                val bandIdx = (i % 32)
                val spectralWeight = abs(finalL) * (1.2f - (bandIdx / 32.0f) * 0.6f)
                tempSpectrum[bandIdx] += spectralWeight
            }

            // Write processed sound to buffer
            if (isInputActive) {
                audioTrack.write(outBuf, 0, bufferSize * 2)
            }

            // Calculation DSP execution duration vs actual buffer time (to compute true real-time CPU % load)
            val elapsedDSpTime = System.nanoTime() - dspStart
            val totalRoundTime = System.nanoTime() - startTime
            val bufferDurationNs = (bufferSize.toDouble() / sampleRate) * 1_000_000_000.0

            val relativeCpu = ((elapsedDSpTime.toDouble() / bufferDurationNs) * 100.0).coerceIn(1.0, 99.0).toInt()
            cpuUsagePercent.value = max(2, relativeCpu)

            // Dynamic latency calculation
            val calculatedLatency = (bufferSize * 1000) / sampleRate + 2
            latencyMs.value = calculatedLatency

            // 4. EMIT REAL-TIME VU METERS AND SPECTRAL GRAPH (60 FPS throttling)
            val now = System.currentTimeMillis()
            if (now - lastSpectrumTime > 25) { // ~40 FPS updates to keep UI extremely light and performant
                lastSpectrumTime = now

                val rmsL = sqrt(rmsLeftSum / bufferSize).toFloat().coerceIn(0.01f, 1.0f)
                val rmsR = sqrt(rmsRightSum / bufferSize).toFloat().coerceIn(0.01f, 1.0f)
                
                _vuLevels.value = Pair(rmsL, rmsR)

                // Decaying peaks
                if (rmsL > peakL) peakL = rmsL else peakL = max(0.02f, peakL - 0.05f)
                if (rmsR > peakR) peakR = rmsR else peakR = max(0.02f, peakR - 0.05f)
                _vuPeak.value = Pair(peakL, peakR)

                // Spectrum dampening & emit
                val finalSpec = FloatArray(32)
                for (b in 0..31) {
                    val rawEner = (tempSpectrum[b] / (bufferSize / 32) * 5.0f).coerceIn(0.01f, 1.0f)
                    // History smoothing
                    spectrumHistory[historyIndex][b] = rawEner
                }
                historyIndex = (historyIndex + 1) % 5

                for (b in 0..31) {
                    var avgSum = 0f
                    for (h in 0..4) avgSum += spectrumHistory[h][b]
                    finalSpec[b] = avgSum / 5f
                }
                _spectrumData.value = finalSpec
            }
        }

        // Clean up
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder: ${e.message}")
        }

        try {
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audioTrack: ${e.message}")
        }
    }

    // Helper functions for autotune matching
    private fun getTunedPitch(inputHz: Double, tuning: PitchCorrectionSettings): Double {
        if (!tuning.isEnabled || tuning.isBypassed) return inputHz

        // A list of notes in C Major scale for snap
        // C=261.63, D=293.66, E=329.63, F=349.23, G=392.00, A=440.00, B=493.88
        val baseFrequencies = when(tuning.scale) {
            PitchScale.MAJOR -> doubleArrayOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88)
            PitchScale.MINOR -> doubleArrayOf(261.63, 293.66, 311.13, 349.23, 392.00, 415.30, 466.16)
            PitchScale.PENTATONIC -> doubleArrayOf(261.63, 293.66, 329.63, 392.00, 440.00)
            PitchScale.CHROMATIC -> doubleArrayOf(
                261.63, 277.18, 293.66, 311.13, 329.63, 349.23,
                369.99, 392.00, 415.30, 440.00, 466.16, 493.88
            )
        }

        // Transpose based on active key
        var transposeFactor = 1.0
        val keyOffsetSemis = when (tuning.key) {
            PitchKey.C -> 0
            PitchKey.CD_CS -> 1
            PitchKey.D -> 2
            PitchKey.DE_DS -> 3
            PitchKey.E -> 4
            PitchKey.F -> 5
            PitchKey.FG_FS -> 6
            PitchKey.G -> 7
            PitchKey.GA_GS -> 8
            PitchKey.A -> 9
            PitchKey.AB_AS -> 10
            PitchKey.B -> 11
        }
        transposeFactor = 2.0.pow(keyOffsetSemis / 12.0)

        val targetFreqList = baseFrequencies.map { it * transposeFactor }

        // Find closest harmonic matched octave frequency
        var bestFitTarget = inputHz
        var minDiff = Double.MAX_VALUE

        for (oct in -2..2) {
            val octMult = 2.0.pow(oct)
            for (freq in targetFreqList) {
                val testFreq = freq * octMult
                val diff = abs(testFreq - inputHz)
                if (diff < minDiff) {
                    minDiff = diff
                    bestFitTarget = testFreq
                }
            }
        }

        // Apply corrective blending
        val blendAmount = tuning.retuneSpeedPercent / 100f
        return inputHz * (1f - blendAmount) + bestFitTarget * blendAmount
    }
}
