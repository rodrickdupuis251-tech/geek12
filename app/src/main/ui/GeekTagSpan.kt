package com.ljx.wechatmod.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.style.ReplacementSpan
import android.widget.TextView

class GeekTagSpan(
    private val text: String,
    private val bgColor: Int,
    private val textColor: Int,
    private val isDynamic: Boolean,
    private val parentView: TextView?
) : ReplacementSpan() {

    private var cachedWidth = -1f
    private val paddingH = 15f 
    private val paddingV = 5f  
    private val marginLeft = 12f 
    
    private val shaderMatrix = Matrix()

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val originalTextSize = paint.textSize
        paint.textSize = originalTextSize * 0.78f 
        cachedWidth = paint.measureText(this.text) + paddingH * 2 + marginLeft
        paint.textSize = originalTextSize 
        return cachedWidth.toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val originalTextSize = paint.textSize
        val originalColor = paint.color
        val originalStyle = paint.style
        val originalShader = paint.shader

        val originalFm = paint.fontMetrics 
        paint.textSize = originalTextSize * 0.78f
        paint.isAntiAlias = true
        val scaledFm = paint.fontMetrics

        val textHeight = scaledFm.descent - scaledFm.ascent
        val bgHeight = textHeight + paddingV * 2
        
        val rectBottom = y + originalFm.descent - 1f
        val rectTop = rectBottom - bgHeight

        val rect = RectF(
            x + marginLeft, 
            rectTop, 
            x + marginLeft + paint.measureText(this.text) + paddingH * 2, 
            rectBottom
        )

        val radius = bgHeight * 0.35f
        val isDarkText = (Color.red(textColor) * 0.299 + Color.green(textColor) * 0.587 + Color.blue(textColor) * 0.114) < 128

        val glowAlpha = if (isDarkText) 90 else 120 
        val glowColor = Color.argb(glowAlpha, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
        paint.setShadowLayer(14f, 0f, 4f, glowColor)

        val hsv = FloatArray(3)
        Color.colorToHSV(bgColor, hsv)
        hsv[1] = Math.max(0f, hsv[1] - 0.05f) 
        hsv[2] = Math.min(1f, hsv[2] + 0.12f)
        val topColor = Color.HSVToColor(hsv)
        
        Color.colorToHSV(bgColor, hsv)
        hsv[2] = Math.max(0f, hsv[2] - 0.08f)
        val bottomColor = Color.HSVToColor(hsv)

        paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, topColor, bottomColor, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, radius, radius, paint)

        paint.clearShadowLayer()

        val glassHighlight = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            intArrayOf(Color.argb(90, 255, 255, 255), Color.TRANSPARENT, Color.argb(20, 255, 255, 255)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = glassHighlight
        canvas.drawRoundRect(rect, radius, radius, paint)

        paint.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            intArrayOf(Color.argb(140, 255, 255, 255), Color.TRANSPARENT, Color.argb(30, 0, 0, 0)),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f 
        val innerRect = RectF(rect.left + 0.6f, rect.top + 0.6f, rect.right - 0.6f, rect.bottom - 0.6f)
        canvas.drawRoundRect(innerRect, radius, radius, paint)

        val textWidth = paint.measureText(this.text)
        
        val textShader = LinearGradient(
            0f, 0f, textWidth * 0.8f, 0f, 
            intArrayOf(textColor, Color.parseColor("#FFFFFF"), textColor), 
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        if (isDynamic && parentView != null) {
            val cycleTime = 2500L
            val timeOffset = System.currentTimeMillis() % cycleTime
            val progress = timeOffset / cycleTime.toFloat()
            
            val sweepStart = rect.left - (textWidth * 0.8f)
            val sweepEnd = rect.right
            val currentTranslate = sweepStart + (sweepEnd - sweepStart) * progress
            
            shaderMatrix.reset()
            shaderMatrix.setTranslate(currentTranslate, 0f)
            textShader.setLocalMatrix(shaderMatrix)
            
            parentView.postInvalidateOnAnimation()
        } else {
            shaderMatrix.reset()
            shaderMatrix.setTranslate(rect.left + textWidth * 0.3f, 0f)
            textShader.setLocalMatrix(shaderMatrix)
        }

        paint.shader = textShader
        paint.style = Paint.Style.FILL
        paint.isFakeBoldText = true 

        if (isDarkText) {
            paint.setShadowLayer(1f, 0f, 1f, Color.argb(60, 255, 255, 255))
        } else {
            paint.setShadowLayer(1.5f, 0f, 1.5f, Color.argb(80, 0, 0, 0))
        }

        val textBaseline = rect.centerY() - (scaledFm.descent + scaledFm.ascent) / 2f
        canvas.drawText(this.text, rect.left + paddingH, textBaseline, paint)

        paint.clearShadowLayer()
        paint.shader = originalShader
        paint.style = originalStyle
        paint.textSize = originalTextSize
        paint.color = originalColor
        paint.isFakeBoldText = false
    }
}
