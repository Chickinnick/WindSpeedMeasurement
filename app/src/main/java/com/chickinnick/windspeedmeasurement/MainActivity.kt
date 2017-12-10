package com.chickinnick.windspeedmeasurement

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.ToggleButton
import com.chickinnick.audioanalyzer.MicrophoneInput
import com.chickinnick.audioanalyzer.MicrophoneInputListener
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {
    lateinit var micInput: MicrophoneInput  // The micInput object provides real time audio.
    lateinit var mdBTextView: TextView
    lateinit var mdBFractionTextView: TextView
    lateinit var mBarLevel: BarLevelDrawable
    private var mGainTextView: TextView? = null
    // The Google ASR input requirements state that audio input sensitivity
    // should be set such that 90 dB SPL at 1000 Hz yields RMS of 2500 for
    // 16-bit samples, i.e. 20 * log_10(2500 / mGain) = 90.
    var mGain = 2500.0 / Math.pow(10.0, 90.0 / 20.0)
    var mOffsetdB = 10.0  // Offset for bar, i.e. 0 lit LEDs at 10 dB.

    // For displaying error in calibration.
    var mDifferenceFromNominal = 0.0
    var mRmsSmoothed: Double = 0.toDouble()  // Temporally filtered version of RMS.
    var mAlpha = 0.9  // Coefficient of IIR smoothing filter for RMS.
    private var mSampleRate: Int = 0  // The audio sampling rate to use.
    private var mAudioSource: Int = 0  // The audio source to use.

    private val audioFrameListenter = AudioFrameListenter()

    /** Called when the activity is first created.  */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Here the micInput object is created for audio capture.
        // It is set up to call this object to handle real time audio frames of
        // PCM samples. The incoming frames will be handled by the
        // processAudioFrame method below.
        micInput = com.chickinnick.audioanalyzer.MicrophoneInput(audioFrameListenter)
        setContentView(R.layout.activity_main)
        Dexter.withActivity(this)
                .withPermission(Manifest.permission.RECORD_AUDIO)
                .withListener(object : PermissionListener {
                    override fun onPermissionGranted(response: PermissionGrantedResponse?) {

                    }

                    override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest?, token: PermissionToken?) {
                    }

                    override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                    }

                })
                .check();
        // Read the layout and construct.

        // Get a handle that will be used in async thread post to update the
        // display.
        mBarLevel = findViewById<BarLevelDrawable>(R.id.bar_level_drawable_view)
        mdBTextView = findViewById<TextView>(R.id.dBTextView)
        mdBFractionTextView = findViewById<TextView>(R.id.dBFractionTextView)
        mGainTextView = findViewById<TextView>(R.id.gain)

        // Toggle Button handler.

        val onOffButton = findViewById<ToggleButton>(
                R.id.on_off_toggle_button)

        val tbListener = object : View.OnClickListener {
            override fun onClick(v: View) {
                if (onOffButton.isChecked) {
                    readPreferences()
                    micInput.setSampleRate(mSampleRate)
                    micInput.setAudioSource(mAudioSource)
                    micInput.start()
                } else {
                    micInput.stop()
                }
            }
        }
        onOffButton.setOnClickListener(tbListener)

        // Level adjustment buttons.

        // Minus 5 dB button event handler.
        val minus5dbButton = findViewById<Button>(R.id.minus_5_db_button)
        val minus5dBButtonListener = DbClickListener(-5.0)
        minus5dbButton.setOnClickListener(minus5dBButtonListener)

        // Minus 1 dB button event handler.
        val minus1dbButton = findViewById<Button>(R.id.minus_1_db_button)
        val minus1dBButtonListener = DbClickListener(-1.0)
        minus1dbButton.setOnClickListener(minus1dBButtonListener)

        // Plus 1 dB button event handler.
        val plus1dbButton = findViewById<Button>(R.id.plus_1_db_button)
        val plus1dBButtonListener = DbClickListener(1.0)
        plus1dbButton.setOnClickListener(plus1dBButtonListener)

        // Plus 5 dB button event handler.
        val plus5dbButton = findViewById<Button>(R.id.plus_5_db_button)
        val plus5dBButtonListener = DbClickListener(5.0)
        plus5dbButton.setOnClickListener(plus5dBButtonListener)
    }

    /**
     * Inner class to handle press of gain adjustment buttons.
     */
    private inner class DbClickListener(private val gainIncrement: Double) : View.OnClickListener {

        override fun onClick(v: View) {
            mGain *= Math.pow(10.0, gainIncrement / 20.0)
            mDifferenceFromNominal -= gainIncrement
            val df = DecimalFormat("##.# dB")
            mGainTextView!!.text = df.format(mDifferenceFromNominal)
        }
    }

    /**
     * Method to read the sample rate and audio source preferences.
     */
    private fun readPreferences() {
        val preferences = getSharedPreferences("LevelMeter",
                Context.MODE_PRIVATE)
        mSampleRate = preferences.getInt("SampleRate", 8000)
        mAudioSource = preferences.getInt("AudioSource",
                MediaRecorder.AudioSource.VOICE_RECOGNITION)
    }

    /**
     * This method gets called by the micInput object owned by this activity.
     * It first computes the RMS value and then it sets up a bit of
     * code/closure that runs on the UI thread that does the actual drawing.
     */
    inner class AudioFrameListenter : MicrophoneInputListener {


        // Variables to monitor UI update and check for slow updates.
        @Volatile private var mDrawing: Boolean = false
        @Volatile private var mDrawingCollided: Int = 0

        override fun processAudioFrame(audioFrame: ByteArray) {
            if (!mDrawing) {
                mDrawing = true
                // Compute the RMS value. (Note that this does not remove DC).
                var rms = 0.0
                for (i in audioFrame.indices) {
                    rms += (audioFrame[i] * audioFrame[i]).toDouble()
                }
                rms = Math.sqrt(rms / audioFrame.size)

                // Compute a smoothed version for less flickering of the display.
                mRmsSmoothed = mRmsSmoothed * mAlpha + (1 - mAlpha) * rms
                val rmsdB = 20.0 * Math.log10(mGain * mRmsSmoothed)

                // Set up a method that runs on the UI thread to update of the LED bar
                // and numerical display.
                mBarLevel.post {
                    // The bar has an input range of [0.0 ; 1.0] and 10 segments.
                    // Each LED corresponds to 6 dB.
                    mBarLevel.level = ((mOffsetdB + rmsdB) / 60)

                    val df = DecimalFormat("##")
                    mdBTextView.text = df.format(20 + rmsdB)

                    val df_fraction = DecimalFormat("#")
                    val one_decimal = Math.round(Math.abs(rmsdB * 10)).toInt() % 10
                    mdBFractionTextView.text = Integer.toString(one_decimal)
                    mDrawing = false
                }
            } else {
                mDrawingCollided++
                Log.v(TAG, "Level bar update collision, i.e. update took longer " +
                        "than 20ms. Collision count" + java.lang.Double.toString(mDrawingCollided.toDouble()))
            }
        }

    }

    companion object {

        private val TAG = "LevelMeterActivity"
    }

}