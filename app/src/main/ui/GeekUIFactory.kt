package com.ljx.wechatmod.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

import com.ljx.wechatmod.core.GeekConfig
import com.ljx.wechatmod.core.GeekLogger
import com.ljx.wechatmod.core.dp
import com.ljx.wechatmod.ui.dialog.GeekSubDialogs

import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.TonalPalette
import java.util.concurrent.ConcurrentHashMap

data class GeekThemeContext(
    val styleMode: Int, 
    val bgCol: Int, val cardCol: Int, val glassStrokeColor: Int,
    val textMainCol: Int, val textSubCol: Int, val accentCol: Int,
    val dividerCol: Int, val rippleCol: Int, 
    val cardCornerRadius: Float, val itemCornerRadius: Float,
    val useStroke: Boolean, val useElevationShadow: Boolean,
    val useDivider: Boolean, val itemSpacing: Int, 
    val showIcons: Boolean, val iconTintAccent: Boolean, val isNight: Boolean 
)

object GeekThemeEngine {
    val layoutStyles = arrayOf("MIUIX 灵动", "Materia")
    private val themeCache = ConcurrentHashMap<String, GeekThemeContext>()

    fun getTheme(context: Context, rawStyleIdx: Int, isNight: Boolean): GeekThemeContext {
        val styleMode = if (rawStyleIdx >= 1) 1 else 0 
        val customAccentHex = GeekConfig.getString("key_custom_accent_color", "#00A4FF")
        
        var seedColorInt = try { Color.parseColor(customAccentHex) } catch (e: Exception) { GeekLogger.logError("UIFactory_ParseColor", e); Color.parseColor("#00A4FF") }
        
        val isDynamic = GeekConfig.isOn("key_dynamic_color")
        if (isDynamic && Build.VERSION.SDK_INT >= 31) {
            try { seedColorInt = context.resources.getColor(android.R.color.system_accent1_500, context.theme) } catch (e: Exception) { GeekLogger.logError("UIFactory_SysColor", e) }
        }
        
        val showIcons = GeekConfig.getBoolean("key_show_icons", true)
        val iconTintAccent = GeekConfig.getBoolean("key_icon_tint_accent", false)

        val cacheKey = "${styleMode}_${isNight}_${seedColorInt}_${showIcons}_${iconTintAccent}"
        themeCache[cacheKey]?.let { return it }

        val hct = Hct.fromInt(seedColorInt)
        val primaryPalette = TonalPalette.fromHueAndChroma(hct.hue, Math.max(hct.chroma, 50.0))
        val surfacePalette = TonalPalette.fromHueAndChroma(hct.hue, 8.0) 
        val neutralVariant = TonalPalette.fromHueAndChroma(hct.hue, 12.0)
        val accentCol = primaryPalette.tone(if (isNight) 80 else 45)            

        val bgCol: Int; val cardCol: Int; val glassStrokeColor: Int
        val dividerCol: Int; val textMainCol: Int; val textSubCol: Int

        if (styleMode == 1) {
            bgCol = surfacePalette.tone(if (isNight) 6 else 98)
            cardCol = surfacePalette.tone(if (isNight) 12 else 95)
            glassStrokeColor = Color.TRANSPARENT 
            dividerCol = neutralVariant.tone(if (isNight) 20 else 90)
            textMainCol = surfacePalette.tone(if (isNight) 95 else 15) 
            textSubCol = if (isNight) Color.argb(160, 255, 255, 255) else neutralVariant.tone(40)
        } else {
            bgCol = if (isNight) Color.parseColor("#000000") else Color.parseColor("#F3F4F6")                 
            cardCol = if (isNight) Color.parseColor("#1C1C1E") else Color.parseColor("#FFFFFF") 
            glassStrokeColor = if (isNight) Color.parseColor("#12FFFFFF") else Color.parseColor("#0A000000") 
            dividerCol = if (isNight) Color.parseColor("#262628") else Color.parseColor("#E5E5EA")
            textMainCol = if (isNight) Color.parseColor("#FFFFFF") else Color.parseColor("#111111") 
            textSubCol = if (isNight) Color.argb(160, 255, 255, 255) else Color.parseColor("#8E8E93")
        }

        val themeContext = GeekThemeContext(
            styleMode = styleMode, bgCol = bgCol, cardCol = cardCol, glassStrokeColor = glassStrokeColor,
            textMainCol = textMainCol, textSubCol = textSubCol, accentCol = accentCol, dividerCol = dividerCol,
            rippleCol = Color.argb(if (styleMode == 1) 35 else 20, Color.red(accentCol), Color.green(accentCol), Color.blue(accentCol)),
            cardCornerRadius = if (styleMode == 1) 28f.dp else 24f.dp, itemCornerRadius = if (styleMode == 1) 0f else 12f.dp, 
            useStroke = false, useElevationShadow = (styleMode == 0), useDivider = (styleMode == 0),
            itemSpacing = 0, showIcons = showIcons, iconTintAccent = iconTintAccent, isNight = isNight
        )

        themeCache[cacheKey] = themeContext
        return themeContext
    }
}

object GeekUIFactory {
    var remixFont: Typeface? = null
    val mediumFont: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    val switchSpringInterpolator = Interpolator { input ->
        val tension = 0.28f 
        (Math.pow(2.0, -10.0 * input.toDouble()) * Math.sin((input.toDouble() - tension / 4.0) * (Math.PI * 2.0) / tension) + 1.0).toFloat()
    }

    fun initFont(act: Activity) {
        if (remixFont == null) {
            try {
                val modCtx = act.createPackageContext("com.ljx.wechatmod", Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE)
                remixFont = Typeface.createFromAsset(modCtx.assets, "remixicon.ttf")
            } catch (e: Exception) { GeekLogger.logError("UIFactory_Font", e) }
        }
    }

    fun applyTouchFeedback(view: View, theme: GeekThemeContext) {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                (v.getTag(0x7E090002) as? SpringAnimation)?.cancel()
                (v.getTag(0x7E090003) as? SpringAnimation)?.cancel()
                (v.getTag(0x7E090011) as? SpringAnimation)?.cancel()
                (v.getTag(0x7E090012) as? SpringAnimation)?.cancel()
            }
        })

        if (theme.styleMode == 1) { 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val rippleColor = ColorStateList.valueOf(theme.rippleCol)
                val currentBg = view.background
                if (currentBg !is RippleDrawable) view.background = RippleDrawable(rippleColor, currentBg, null)
            }
        } else {
            view.setOnTouchListener { v, ev ->
                val springX = v.getTag(0x7E090002) as? SpringAnimation ?: SpringAnimation(v, DynamicAnimation.SCALE_X).also { v.setTag(0x7E090002, it) }
                val springY = v.getTag(0x7E090003) as? SpringAnimation ?: SpringAnimation(v, DynamicAnimation.SCALE_Y).also { v.setTag(0x7E090003, it) }
                val rotX = v.getTag(0x7E090011) as? SpringAnimation ?: SpringAnimation(v, DynamicAnimation.ROTATION_X).also { v.setTag(0x7E090011, it) }
                val rotY = v.getTag(0x7E090012) as? SpringAnimation ?: SpringAnimation(v, DynamicAnimation.ROTATION_Y).also { v.setTag(0x7E090012, it) }

                v.cameraDistance = v.resources.displayMetrics.density * 5000f

                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> { 
                        val centerX = v.width / 2f
                        val centerY = v.height / 2f
                        
                        val maxRot = 6f
                        val rotateY = ((ev.x - centerX) / centerX) * maxRot
                        val rotateX = -((ev.y - centerY) / centerY) * maxRot 
                        
                        rotX.spring = SpringForce(rotateX).setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(0.6f)
                        rotY.spring = SpringForce(rotateY).setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(0.6f)
                        rotX.start(); rotY.start()
                        
                        springX.spring = SpringForce(0.96f).setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(0.85f)
                        springY.spring = SpringForce(0.96f).setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(0.85f)
                        springX.start(); springY.start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { 
                        rotX.spring = SpringForce(0f).setStiffness(SpringForce.STIFFNESS_LOW).setDampingRatio(0.5f)
                        rotY.spring = SpringForce(0f).setStiffness(SpringForce.STIFFNESS_LOW).setDampingRatio(0.5f)
                        rotX.start(); rotY.start()
                        
                        springX.spring = SpringForce(1f).setStiffness(SpringForce.STIFFNESS_LOW).setDampingRatio(0.45f)
                        springY.spring = SpringForce(1f).setStiffness(SpringForce.STIFFNESS_LOW).setDampingRatio(0.45f)
                        springX.start(); springY.start()
                    }
                }
                false
            }
        }
    }

    private fun createModernSwitch(context: Context, isChecked: Boolean, theme: GeekThemeContext, onToggle: (Boolean) -> Unit): View {
        val isMIUI = theme.styleMode == 0
        val trackWidth = if (isMIUI) 52.dp else 46.dp
        val trackHeight = if (isMIUI) 28.dp else 26.dp
        val thumbSize = if (isMIUI) 24.dp else 22.dp
        val padding = 2.dp
        val travelDistance = (trackWidth - thumbSize - padding * 2).toFloat()
        val trackOffColor = Color.parseColor(if (theme.isNight) "#39393D" else "#E9E9EA")
        val thumbOffColor = Color.WHITE

        val track = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(trackWidth, trackHeight)
            background = GradientDrawable().apply { setColor(if (isChecked) theme.accentCol else trackOffColor); cornerRadius = 100f.dp }
            if (isMIUI && !theme.isNight) {
                (background as GradientDrawable).setStroke(1.dp, Color.parseColor("#15000000"))
            }
        }

        val thumb = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(thumbSize, thumbSize).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START; leftMargin = padding }
            background = GradientDrawable().apply { setColor(thumbOffColor); cornerRadius = 100f.dp }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { 
                elevation = if (isMIUI) 6f.dp else 4f.dp 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    outlineSpotShadowColor = Color.parseColor(if(theme.isNight) "#000000" else "#50000000")
                    outlineAmbientShadowColor = Color.parseColor(if(theme.isNight) "#000000" else "#30000000") 
                }
            }
            translationX = if (isChecked) travelDistance else 0f
        }
        track.addView(thumb)

        var currentState = isChecked
        var isTracking = false 
        var lastToggleTime = 0L
        var colorAnimator: android.animation.ValueAnimator? = null

        track.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                colorAnimator?.cancel()
                (thumb.getTag(0x7E090005) as? SpringAnimation)?.cancel()
            }
        })

        fun toggleSwitch() {
            val now = System.currentTimeMillis()
            if (now - lastToggleTime < 250) return
            lastToggleTime = now

            GeekConfig.richHaptic(track, 1)
            currentState = !currentState
            onToggle(currentState)
            
            colorAnimator?.cancel()
            val bg = track.background as GradientDrawable
            
            val animColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.animation.ValueAnimator.ofArgb(if (currentState) trackOffColor else theme.accentCol, if (currentState) theme.accentCol else trackOffColor)
            } else {
                val anim = android.animation.ValueAnimator.ofInt(if (currentState) trackOffColor else theme.accentCol, if (currentState) trackOffColor else theme.accentCol)
                anim.setEvaluator(android.animation.ArgbEvaluator())
                anim
            }
            
            animColor.addUpdateListener { bg.setColor(it.animatedValue as Int) }
            animColor.duration = 250
            colorAnimator = animColor
            animColor.start()

            val fluidAnim = android.animation.ValueAnimator.ofFloat(1f, 1.45f, 1f)
            fluidAnim.addUpdateListener { thumb.scaleX = it.animatedValue as Float }
            fluidAnim.duration = 350
            fluidAnim.interpolator = DecelerateInterpolator()
            thumb.pivotX = if (currentState) 0f else thumbSize.toFloat() 
            thumb.pivotY = thumbSize / 2f
            fluidAnim.start()

            val springX = thumb.getTag(0x7E090005) as? SpringAnimation ?: SpringAnimation(thumb, DynamicAnimation.TRANSLATION_X).also { thumb.setTag(0x7E090005, it) }
            val stiffness = if (isMIUI) SpringForce.STIFFNESS_LOW else SpringForce.STIFFNESS_MEDIUM
            springX.spring = SpringForce(if (currentState) travelDistance else 0f).setStiffness(stiffness).setDampingRatio(0.65f)
            springX.start()
        }

        track.setOnClickListener {
            if (!isTracking) toggleSwitch()
        }

        if (isMIUI) {
            track.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isTracking = true
                        val anim = android.animation.ValueAnimator.ofInt(thumbSize, thumbSize + 7.dp)
                        anim.addUpdateListener { 
                            val lp = thumb.layoutParams as FrameLayout.LayoutParams
                            lp.width = it.animatedValue as Int
                            if (currentState) {
                                thumb.translationX = travelDistance - (lp.width - thumbSize)
                            }
                            thumb.layoutParams = lp
                        }
                        anim.duration = 150
                        anim.interpolator = DecelerateInterpolator()
                        anim.start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val isClick = event.actionMasked == MotionEvent.ACTION_UP
                        val anim = android.animation.ValueAnimator.ofInt(thumb.width, thumbSize)
                        anim.addUpdateListener {
                            val lp = thumb.layoutParams as FrameLayout.LayoutParams
                            lp.width = it.animatedValue as Int
                            thumb.layoutParams = lp
                        }
                        anim.duration = 350
                        anim.interpolator = switchSpringInterpolator
                        anim.start()

                        if (isClick) toggleSwitch()
                        else {
                            val springX = thumb.getTag(0x7E090005) as? SpringAnimation ?: SpringAnimation(thumb, DynamicAnimation.TRANSLATION_X).also { thumb.setTag(0x7E090005, it) }
                            springX.spring = SpringForce(if (currentState) travelDistance else 0f).setStiffness(SpringForce.STIFFNESS_LOW).setDampingRatio(0.65f)
                            springX.start()
                        }
                        
                        track.postDelayed({ isTracking = false }, 50)
                    }
                }
                true
            }
        }
        return track
    }

    fun addGroup(act: Activity, container: LinearLayout, title: String, theme: GeekThemeContext, builder: (LinearLayout) -> Unit) {
        container.addView(TextView(act).apply { text = title; textSize = 13f; setTextColor(theme.accentCol); setPadding(32.dp, 16.dp, 24.dp, 8.dp); setTypeface(mediumFont); letterSpacing = 0.03f; if (theme.styleMode == 0) alpha = 0.9f })
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(24.dp, 0, 24.dp, 24.dp) }
            background = GradientDrawable().apply { setColor(theme.cardCol); cornerRadius = theme.cardCornerRadius; if (theme.useStroke) setStroke(1.dp, theme.glassStrokeColor) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { 
                if (theme.useElevationShadow) {
                    elevation = 16f.dp 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { outlineAmbientShadowColor = Color.parseColor(if(theme.isNight) "#000000" else "#03000000"); outlineSpotShadowColor = Color.parseColor(if(theme.isNight) "#000000" else "#05000000") }
                } else elevation = 0f 
                clipToOutline = true 
            }
        }
        builder(card); container.addView(card)
    }

    fun addRow(
        act: Activity, container: LinearLayout, theme: GeekThemeContext, 
        icon: String, name: String, key: String, type: Int = 0, 
        desc: String? = null, defVal: String = "", isLastItem: Boolean = false, 
        onProgress: ((Int) -> Unit)? = null, onClickAction: (() -> Unit)? = null, 
        onToggle: ((Boolean) -> Unit)? = null
    ): LinearLayout {
        initFont(act) 
        val wrapper = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, -2) }
        
        val row = LinearLayout(act).apply { setPadding(16.dp, 16.dp, 16.dp, 16.dp); gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = theme.itemSpacing }; isClickable = true }
        
        var targetIconView: TextView? = null
        if (theme.showIcons && icon.isNotEmpty()) {
            targetIconView = TextView(act).apply { text = icon; gravity = Gravity.CENTER; if (remixFont != null) typeface = remixFont; setTextColor(if (theme.iconTintAccent) theme.accentCol else theme.textMainCol); textSize = 22f; alpha = if (theme.iconTintAccent) 1f else 0.95f; layoutParams = LinearLayout.LayoutParams(34.dp, 34.dp).apply { rightMargin = 16.dp } }
            row.addView(targetIconView)
        }
        
        val textContainer = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        textContainer.addView(TextView(act).apply { text = name; textSize = 15.5f; setTextColor(theme.textMainCol); letterSpacing = -0.02f; setTypeface(mediumFont, Typeface.BOLD) })
        if (desc != null) textContainer.addView(TextView(act).apply { text = desc; textSize = 11.5f; setTextColor(theme.textSubCol); setPadding(0, 4.dp, 0, 0); letterSpacing = 0.01f; setTypeface(mediumFont, Typeface.NORMAL) })
        row.addView(textContainer)

        when (type) {
            1 -> { 
                val rightLayout = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val valText = TextView(act).apply { text = GeekConfig.getString(key, defVal); textSize = 15f; setTextColor(theme.accentCol); setPadding(0, 0, 8.dp, 0) }
                rightLayout.addView(valText)
                row.addView(rightLayout)

                var lastClickTime = 0L
                row.setOnClickListener {
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime > 350) {
                        lastClickTime = now
                        GeekConfig.richHaptic(it, 3)
                        GeekSubDialogs.showInputDialog(act, name, desc ?: "", key, defVal, theme) { newValue ->
                            valText.text = newValue
                        }
                    }
                }
            }
            0 -> {
                val modernSwitch = createModernSwitch(act, GeekConfig.isOn(key), theme) { newState -> 
                    GeekConfig.putBoolean(key, newState)
                    
                    targetIconView?.let { iconV ->
                        val springRot = iconV.getTag(0x7E0A0001) as? SpringAnimation ?: SpringAnimation(iconV, DynamicAnimation.ROTATION).apply {
                            spring = SpringForce().setStiffness(SpringForce.STIFFNESS_LOW).setDampingRatio(0.6f)
                        }.also { iconV.setTag(0x7E0A0001, it) }
                        
                        springRot.animateToFinalPosition(if (newState) 360f else 0f)
                    }

                    onToggle?.invoke(newState) 
                }
                row.setOnClickListener { modernSwitch.performClick() } 
                row.addView(modernSwitch)
            }
            2 -> {
                val rightLayout = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                rightLayout.addView(TextView(act).apply { text = defVal; textSize = 14f; setTextColor(theme.textSubCol); setPadding(0,0,8.dp,0) })
                rightLayout.addView(TextView(act).apply { text = "\uEA6E"; textSize = 18f; setTextColor(Color.parseColor(if(theme.isNight) "#505050" else "#C0C0C0")); if (remixFont != null) typeface = remixFont })
                row.addView(rightLayout)
                
                var lastClickTime = 0L
                row.setOnClickListener { 
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime > 350) {
                        lastClickTime = now
                        GeekConfig.richHaptic(it, 3)
                        onClickAction?.invoke() 
                    }
                }
            }
            3 -> {
                val sliderLayout = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1.3f); gravity = Gravity.CENTER_VERTICAL or Gravity.END }
                val valText = TextView(act).apply { text = "${GeekConfig.getInt(key, defVal.toInt())}"; textSize = 13.5f; setTextColor(theme.accentCol); setTypeface(mediumFont); setPadding(0, 0, 6.dp, 0) }
                val seekBar = SeekBar(act).apply {
                    max = 100; progress = GeekConfig.getInt(key, defVal.toInt()); layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { progressTintList = ColorStateList.valueOf(theme.accentCol); thumbTintList = ColorStateList.valueOf(theme.accentCol) }
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { valText.text = "$progress"; if (fromUser) { GeekConfig.putInt(key, progress); onProgress?.invoke(progress) } }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) { GeekConfig.richHaptic(this@apply, 2) } })
                }
                sliderLayout.addView(seekBar); sliderLayout.addView(valText); row.addView(sliderLayout)
            }
        }
        
        applyTouchFeedback(row, theme)
        wrapper.addView(row)
        
        if (theme.useDivider && !isLastItem) {
            val leftMargin = if (theme.showIcons && icon.isNotEmpty()) 66.dp else 16.dp
            wrapper.addView(View(act).apply { layoutParams = LinearLayout.LayoutParams(-1, 1).apply { setMargins(leftMargin, 0, 0, 0) }; setBackgroundColor(theme.dividerCol) })
        }
        
        container.addView(wrapper)
        return wrapper 
    }
}

class AcrylicNoiseDrawable(val isNight: Boolean) : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint()
    private var shader: android.graphics.BitmapShader? = null
    
    override fun draw(canvas: android.graphics.Canvas) {
        if (shader == null) {
            val size = 256
            val pixels = IntArray(size * size)
            val rnd = java.util.Random()
            val alphaBase = if (isNight) 10 else 18
            for (i in pixels.indices) {
                val a = rnd.nextInt(alphaBase)
                val c = if (isNight) 255 else 0
                pixels[i] = android.graphics.Color.argb(a, c, c, c)
            }
            val bmp = android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
            shader = android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
            paint.shader = shader
        }
        canvas.drawRect(bounds, paint)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
