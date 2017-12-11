package com.chickinnick.windspeedmeasurement

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.util.AttributeSet
import android.view.View

class BarLevelDrawable : View {
    private var mDrawable: ShapeDrawable? = null
    private var mLevel = 0.1

    internal val segmentColors = intArrayOf(-0xaaaa01, -0xaaaa01, -0xff0100, -0xff0100, -0xff0100, -0xff0100, -0x100, -0x100, -0x10000, -0x10000)
    internal val segmentOffColor = -0xaaaaab

    /**
     * Set the bar level. The level should be in the range [0.0 ; 1.0], i.e.
     * 0.0 gives no lit LEDs and 1.0 gives full scale.
     *
     * @param level the LED level in the range [0.0 ; 1.0].
     */
    var level: Double
        get() = mLevel
        set(level) {
            mLevel = level
            invalidate()
        }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initBarLevelDrawable()
    }

    constructor(context: Context) : super(context) {
        initBarLevelDrawable()
    }

    private fun initBarLevelDrawable() {
        mLevel = 0.1
    }

    private fun drawBar(canvas: Canvas) {
        val padding = 5 // Padding on both sides.
        var x = 0
        val y = 10

        val width = Math.floor((width / segmentColors.size).toDouble()).toInt() - 2 * padding
        val height = 50

        mDrawable = ShapeDrawable(RectShape())
        for (i in segmentColors.indices) {
            x = x + padding
            if (mLevel * segmentColors.size > i + 0.5) {
                mDrawable!!.paint.color = segmentColors[i]
            } else {
                mDrawable!!.paint.color = segmentOffColor
            }
            mDrawable!!.setBounds(x, y, x + width, y + height)
            mDrawable!!.draw(canvas)
            x = x + width + padding
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawBar(canvas)
    }
}
