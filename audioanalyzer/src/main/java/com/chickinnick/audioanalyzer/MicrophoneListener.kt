package com.chickinnick.audioanalyzer


/**
 * This is the interface to the MicrophoneInput class for wrapping
 * the audio input.
 *
 * Implement this interface to get real-time audio frames of PCM samples.
 *
 * @author Trausti Kristjansson
 */
interface MicrophoneInputListener {
    /**
     * processAudioFrame gets called periodically, e.g. every 20ms with PCM
     * audio samples.
     *
     * @param audioFrame this is an array of samples, e.g. if the sampling rate
     * is 8000 samples per second, then the array should contain 160 16 bit
     * samples.
     */
    fun processAudioFrame(audioFrame: ByteArray)
}
