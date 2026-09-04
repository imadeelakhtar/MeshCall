package com.example.meshcall

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private var audioHash: Int = 0
    private var progress: Float = 0f
    private val bars = FloatArray(40)
    
    fun setAudioPath(path: String) {
        if (audioHash == path.hashCode()) return
        audioHash = path.hashCode()
        generateDeterministicWaveform()
        invalidate()
    }
    
    fun setProgress(progress: Float) {
        this.progress = progress.coerceIn(0f, 1f)
        invalidate()
    }
    
    fun setColors(baseColor: Int, progressColor: Int) {
        paint.color = baseColor
        progressPaint.color = progressColor
        invalidate()
    }

    private fun generateDeterministicWaveform() {
        val random = java.util.Random(audioHash.toLong())
        for (i in bars.indices) {
            bars[i] = 0.2f + random.nextFloat() * 0.8f
        }
        for (i in 1 until bars.size - 1) {
            bars[i] = (bars[i-1] + bars[i] + bars[i+1]) / 3f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return
        
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = 4f
        val totalGaps = (bars.size - 1) * gap
        val barWidth = (w - totalGaps) / bars.size
        
        paint.strokeWidth = barWidth
        progressPaint.strokeWidth = barWidth
        
        for (i in bars.indices) {
            val barHeight = h * bars[i]
            val x = i * (barWidth + gap) + barWidth / 2f
            val yStart = (h - barHeight) / 2f
            val yEnd = yStart + barHeight
            
            val barProgress = i.toFloat() / bars.size
            if (barProgress <= progress) {
                canvas.drawLine(x, yStart, x, yEnd, progressPaint)
            } else {
                canvas.drawLine(x, yStart, x, yEnd, paint)
            }
        }
    }
}
