package com.chickinnick.audioanalyzer

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

open class MicrophoneInput(private val mListener: MicrophoneInputListener) : Runnable {
    var mSampleRate = 8000
    var mAudioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION
    val mChannelConfig = AudioFormat.CHANNEL_IN_MONO
    val mAudioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var mRunning: Boolean = false
    lateinit var recorder: AudioRecord

    private lateinit var mThread: Thread

    var mTotalSamples = 0

    fun setSampleRate(sampleRate: Int) {
        mSampleRate = sampleRate
    }

    fun setAudioSource(audioSource: Int) {
        mAudioSource = audioSource
    }

    fun start() {
        if (false == mRunning) {
            mRunning = true
            mThread = Thread(this)
            mThread.start()
        }
    }

    fun stop() {
        try {
            if (mRunning) {
                mRunning = false
                mThread.join()
            }
        } catch (e: InterruptedException) {
            Log.v(TAG, "InterruptedException.", e)
        }

    }

    override fun run() {
        // Buffer for 20 milliseconds of data, e.g. 160 samples at 8kHz.
        val buffer20ms = ByteArray(mSampleRate / 50)
        // Buffer size of AudioRecord buffer, which will be at least 1 second.
        val buffer1000msSize = bufferSize(mSampleRate, mChannelConfig,
                mAudioFormat)

        try {
            recorder = AudioRecord(
                    mAudioSource,
                    mSampleRate,
                    mChannelConfig,
                    mAudioFormat,
                    buffer1000msSize)
            recorder.startRecording()

            while (mRunning) {
                val numSamples = recorder.read(buffer20ms, 0, buffer20ms.size)
                mTotalSamples += numSamples
                mListener.processAudioFrame(buffer20ms)
            }
            recorder.stop()
        } catch (x: Throwable) {
            Log.v(TAG, "Error reading audio", x)
        } finally {
        }
    }

    fun totalSamples(): Int {
        return mTotalSamples
    }

    fun setTotalSamples(totalSamples: Int) {
        mTotalSamples = totalSamples
    }

    /**
     * Helper method to find a buffer size for AudioRecord which will be at
     * least 1 second.
     *
     * @param sampleRateInHz the sample rate expressed in Hertz.
     * @param channelConfig describes the configuration of the audio channels.
     * @param audioFormat the format in which the audio data is represented.
     * @return buffSize the size of the audio record input buffer.
     */
    private fun bufferSize(sampleRateInHz: Int, channelConfig: Int,
                           audioFormat: Int): Int {
        var buffSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig,
                audioFormat)
        if (buffSize < sampleRateInHz) {
            buffSize = sampleRateInHz
        }
        return buffSize
    }

    companion object {

        private val TAG = "MicrophoneInput"
    }
}
