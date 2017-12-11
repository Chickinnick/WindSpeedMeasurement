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
    lateinit var micInput: MicrophoneInput
    lateinit var mdBTextView: TextView
    lateinit var mdBFractionTextView: TextView
    lateinit var mBarLevel: BarLevelDrawable
    lateinit var mGainTextView: TextView

    // The Google ASR input requirements state that audio input sensitivity
    // should be set such that 90 dB SPL at 1000 Hz yields RMS of 2500 for
    // 16-bit samples, i.e. 20 * log_10(2500 / mGain) = 90.
    var mGain = 2500.0 / Math.pow(10.0, 90.0 / 20.0)
    var mOffsetdB = 10.0  // Offset for bar, i.e. 0 lit LEDs at 10 dB.

    var mDifferenceFromNominal = 0.0
    var mRmsSmoothed: Double = 0.toDouble()  // Temporally filtered version of RMS.
    var mAlpha = 0.9  // Coefficient of IIR smoothing filter for RMS.
    private var mSampleRate: Int = 0  // The audio sampling rate to use.
    private var mAudioSource: Int = 0  // The audio source to use.

    private val audioFrameListenter = AudioFrameListenter()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        micInput = MicrophoneInput(audioFrameListenter)
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

        mBarLevel = findViewById<BarLevelDrawable>(R.id.bar_level_drawable_view)
        mdBTextView = findViewById<TextView>(R.id.dBTextView)
        mdBFractionTextView = findViewById<TextView>(R.id.dBFractionTextView)
        mGainTextView = findViewById<TextView>(R.id.gain)
        val onOffButton = findViewById<ToggleButton>(R.id.on_off_toggle_button)
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
        val minus5dbButton = findViewById<Button>(R.id.minus_5_db_button)
        val minus5dBButtonListener = DbClickListener(-5.0)
        minus5dbButton.setOnClickListener(minus5dBButtonListener)
        val minus1dbButton = findViewById<Button>(R.id.minus_1_db_button)
        val minus1dBButtonListener = DbClickListener(-1.0)
        minus1dbButton.setOnClickListener(minus1dBButtonListener)
        val plus1dbButton = findViewById<Button>(R.id.plus_1_db_button)
        val plus1dBButtonListener = DbClickListener(1.0)
        plus1dbButton.setOnClickListener(plus1dBButtonListener)
        val plus5dbButton = findViewById<Button>(R.id.plus_5_db_button)
        val plus5dBButtonListener = DbClickListener(5.0)
        plus5dbButton.setOnClickListener(plus5dBButtonListener)
    }

    private inner class DbClickListener(private val gainIncrement: Double) : View.OnClickListener {

        override fun onClick(v: View) {
            mGain *= Math.pow(10.0, gainIncrement / 20.0)
            mDifferenceFromNominal -= gainIncrement
            val df = DecimalFormat("##.# dB")
            mGainTextView.text = df.format(mDifferenceFromNominal)
        }
    }

    private fun readPreferences() {
        val preferences = getSharedPreferences("LevelMeter",
                Context.MODE_PRIVATE)
        mSampleRate = preferences.getInt("SampleRate", 8000)
        mAudioSource = preferences.getInt("AudioSource",
                MediaRecorder.AudioSource.VOICE_RECOGNITION)
    }
    inner class AudioFrameListenter : MicrophoneInputListener {

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
    private val TAG = "LevelMeterActivity"
}