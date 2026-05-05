package com.ljx.wechatmod.hook

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ljx.wechatmod.core.GeekConfig
import com.ljx.wechatmod.core.GeekLogger
import com.ljx.wechatmod.ui.SettingsDialog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

object UIHook {
    private var currentActRef: java.lang.ref.WeakReference<Activity>? = null

    private fun fastFindChatTitleView(root: View): TextView? {
        val q = java.util.LinkedList<View>().apply { add(root) }
        var bestFit: TextView? = null
        val density = root.resources.displayMetrics.density
        val topLimit = root.resources.displayMetrics.heightPixels * 0.15f 
        
        while (q.isNotEmpty()) {
            val v = q.poll()
            if (v is TextView && v.visibility == View.VISIBLE && v.text.isNotEmpty()) {
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                val y = loc[1]
                
                if (y > 10 * density && y < topLimit) {
                    if (v.textSize > 14f * density) {
                        if (bestFit == null || v.textSize > bestFit.textSize) {
                            bestFit = v
                        }
                    }
                }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) q.add(v.getChildAt(i))
        }
        return bestFit
    }

    fun init() {
        XposedBridge.hookAllMethods(Activity::class.java, "onResume", object : XC_MethodHook() {
            override fun afterHookedMethod(p: MethodHookParam) {
                val act = p.thisObject as Activity
                val actName = act.javaClass.name
                val rootView = act.window.decorView as? ViewGroup ?: return
                
                currentActRef = java.lang.ref.WeakReference(act)
                try { GeekConfig.topActivityName = actName } catch (e:Throwable) { GeekLogger.logError("UIHook_TopAct", e) }

                if (actName.contains("LauncherUI")) {
                    if (GeekConfig.isOn("key_floating_settings")) {
                        GeekConfig.safeRun("SetBtn") { injectBtn(rootView, act, "geek_settings_btn", "Set", 300) { SettingsDialog.showGeekSettings(act) } }
                    } else { rootView.findViewWithTag<View>("geek_settings_btn")?.visibility = View.GONE }
                }
                
                if (actName.contains("ChattingUI")) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (act.isFinishing || act.isDestroyed) return@postDelayed
                        try {
                            injectTitleSpoofer(act, rootView)
                        } catch (e: Throwable) {
                            GeekLogger.logError("TitleSpoofer", e)
                        }
                    }, 800)
                }
            }
        })
    }

    private fun injectBtn(rootView: ViewGroup, act: Activity, tag: String, icon: String, top: Int, onClick: () -> Unit) {
        var b = rootView.findViewWithTag<View>(tag)
        if (b != null) { b.visibility = View.VISIBLE; return }
        b = TextView(act).apply {
            setTag(tag); text = icon; textSize = 18f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply { setColor(Color.parseColor("#99000000")); cornerRadius = 45f }
            setPadding(15, 15, 15, 15); elevation = 10f
        }
        val lp = FrameLayout.LayoutParams(120, 120).apply { gravity = Gravity.TOP or Gravity.START; leftMargin = 50; topMargin = top }
        var lx = 0f; var ly = 0f; var mv = false
        b.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lx = ev.rawX; ly = ev.rawY; mv = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - lx; val dy = ev.rawY - ly
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) { mv = true; v.x += dx; v.y += dy; lx = ev.rawX; ly = ev.rawY }
                }
                MotionEvent.ACTION_UP -> { if (!mv) onClick() }
            }
            true
        }
        if (rootView is FrameLayout) rootView.addView(b, lp)
    }

    private fun injectTitleSpoofer(act: Activity, root: ViewGroup) {
        val titleTv = fastFindChatTitleView(root) ?: return
        
        if (titleTv.getTag(0x7E120015) == true) return
        titleTv.setTag(0x7E120015, true)

        val originalColor = titleTv.getTag(0x7E120007) as? Int ?: titleTv.currentTextColor
        titleTv.setTag(0x7E120007, originalColor)

        var currentOriginalText = titleTv.text.toString()

        fun getStableKey(text: String): String = text.replace(Regex("\\(\\d+\\)$"), "").trim()

        titleTv.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (titleTv.getTag(0x7E120008) == true) return
                
                val newText = s?.toString() ?: ""
                if (newText.isEmpty()) return
                currentOriginalText = newText
                
                if (!GeekConfig.isOn("key_transfer_spoof_on")) return

                val stableKey = getStableKey(newText)
                val spoofText = GeekConfig.getString("top_title_text_$stableKey", "")
                val spoofColor = GeekConfig.getInt("top_title_color_$stableKey", 0)

                if (spoofText.isNotEmpty() || spoofColor != 0) {
                    titleTv.setTag(0x7E120008, true)
                    if (spoofText.isNotEmpty()) titleTv.text = spoofText
                    if (spoofColor != 0) titleTv.setTextColor(spoofColor) else titleTv.setTextColor(originalColor)
                    titleTv.setTag(0x7E120008, false)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        titleTv.isClickable = true
        titleTv.setOnClickListener { v ->
            val isOn = GeekConfig.isOn("key_transfer_spoof_on")
            
            if (isOn) {
                val now = System.currentTimeMillis()
                val lastClick = v.getTag(0x7E120012) as? Long ?: 0L
                
                if (now - lastClick < 300) {
                    GeekConfig.richHaptic(v, 1)
                    showTitleSpoofDialog(act, titleTv, currentOriginalText)
                    v.setTag(0x7E120012, 0L)
                    return@setOnClickListener 
                }
                v.setTag(0x7E120012, now)
            }
            
            var parentView = v.parent as? View
            while (parentView != null) {
                if (parentView.isClickable || parentView.hasOnClickListeners()) {
                    parentView.performClick()
                    break
                }
                parentView = parentView.parent as? View
            }
        }
        
        titleTv.text = titleTv.text
    }

    private fun showTitleSpoofDialog(context: Context, titleTv: TextView, originalText: String) {
        val dialog = android.app.Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        val dp = context.resources.displayMetrics.density
        
        val stableKey = originalText.replace(Regex("\\(\\d+\\)$"), "").trim()
        val currentSpoofText = GeekConfig.getString("top_title_text_$stableKey", "")
        var currentSpoofColor = GeekConfig.getInt("top_title_color_$stableKey", 0)

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
            setOnClickListener { dialog.dismiss() }
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 16f * dp
            }
            setPadding((20f * dp).toInt(), (20f * dp).toInt(), (20f * dp).toInt(), (20f * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(-1, -2).apply {
                gravity = Gravity.CENTER
                leftMargin = (30f * dp).toInt()
                rightMargin = (30f * dp).toInt()
            }
            setOnClickListener { } 
        }

        panel.addView(TextView(context).apply {
            text = "顶部标题自定义"
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, (15f * dp).toInt())
        })

        val input = android.widget.EditText(context).apply {
            setText(if (currentSpoofText.isNotEmpty()) currentSpoofText else originalText)
            textSize = 16f
            setTextColor(if (currentSpoofColor != 0) currentSpoofColor else Color.BLACK)
            setSingleLine(true)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5F5F5"))
                cornerRadius = 8f * dp
                setStroke((1f * dp).toInt(), Color.parseColor("#DDDDDD"))
            }
            setPadding((12f * dp).toInt(), (10f * dp).toInt(), (12f * dp).toInt(), (10f * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (15f * dp).toInt() }
            setSelection(text.length)
        }
        panel.addView(input)

        panel.addView(TextView(context).apply {
            text = "选择标题颜色"
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 0, 0, (8f * dp).toInt())
        })

        val colorLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (20f * dp).toInt() }
        }
        
        val colors = intArrayOf(
            0, Color.parseColor("#E57373"), Color.parseColor("#64B5F6"),
            Color.parseColor("#81C784"), Color.parseColor("#FFB300"), Color.parseColor("#BA68C8")
        )
        
        val colorViews = mutableListOf<View>()
        colors.forEach { col ->
            val colorBtn = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, (36f * dp).toInt(), 1f).apply { setMargins((4f*dp).toInt(), 0, (4f*dp).toInt(), 0) }
                val inner = View(context).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (col == 0) Color.parseColor("#E0E0E0") else col) }
                    layoutParams = FrameLayout.LayoutParams((28f * dp).toInt(), (28f * dp).toInt()).apply { gravity = Gravity.CENTER }
                }
                if (col == 0) inner.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke((1.5f*dp).toInt(), Color.parseColor("#999999")) }
                addView(inner)
                
                val outline = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke((2f*dp).toInt(), Color.parseColor("#333333")) }
                background = if (col == currentSpoofColor) outline else null
                
                setOnClickListener {
                    GeekConfig.richHaptic(this, 1)
                    currentSpoofColor = col
                    colorViews.forEach { it.background = null }
                    background = outline
                    if (col != 0) input.setTextColor(col) else input.setTextColor(Color.BLACK)
                }
            }
            colorViews.add(colorBtn)
            colorLayout.addView(colorBtn)
        }
        panel.addView(colorLayout)

        val btnRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2) }
        
        val btnClear = TextView(context).apply {
            text = "恢复默认"
            textSize = 15f; setTextColor(Color.parseColor("#666666")); gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply { setColor(Color.parseColor("#F0F0F0")); cornerRadius = 8f * dp }
            layoutParams = LinearLayout.LayoutParams(0, (42f * dp).toInt(), 1f).apply { rightMargin = (8f * dp).toInt() }
            setOnClickListener {
                GeekConfig.putString("top_title_text_$stableKey", "")
                GeekConfig.putInt("top_title_color_$stableKey", 0)
                
                titleTv.setTag(0x7E120008, true)
                titleTv.text = originalText
                titleTv.setTextColor(titleTv.getTag(0x7E120007) as? Int ?: Color.BLACK)
                titleTv.setTag(0x7E120008, false)
                
                dialog.dismiss()
            }
        }
        
        val btnSave = TextView(context).apply {
            text = "保存生效"
            textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply { setColor(Color.parseColor("#66B3FF")); cornerRadius = 8f * dp }
            layoutParams = LinearLayout.LayoutParams(0, (42f * dp).toInt(), 1f).apply { leftMargin = (8f * dp).toInt() }
            setOnClickListener {
                val newText = input.text.toString().trim()
                if (newText != originalText || currentSpoofColor != 0) {
                    GeekConfig.putString("top_title_text_$stableKey", if (newText == originalText) "" else newText)
                    GeekConfig.putInt("top_title_color_$stableKey", currentSpoofColor)
                    
                    titleTv.text = if (newText.isEmpty()) originalText else newText
                }
                dialog.dismiss()
            }
        }
        
        btnRow.addView(btnClear)
        btnRow.addView(btnSave)
        panel.addView(btnRow)

        root.addView(panel)
        dialog.setContentView(root)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }
}
