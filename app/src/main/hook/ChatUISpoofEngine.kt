package com.ljx.wechatmod.hook

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
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ljx.wechatmod.core.ChatState
import com.ljx.wechatmod.core.GeekConfig
import com.ljx.wechatmod.core.MsgConstants
import com.ljx.wechatmod.core.MessageSender
import com.ljx.wechatmod.core.GeekReflectionPool

object ChatUISpoofEngine {
    private var neatGetTextMethod: java.lang.reflect.Method? = null
    private var neatSetTextMethod: java.lang.reflect.Method? = null

    fun applyUniversalSpoof(itemView: View, vh: ChatState.GeekViewHolder, type: Int, msgId: Long, msgInfo: Any, isSend: Int, talker: String) {
        if (!GeekConfig.isOn("key_transfer_spoof_on")) {
            itemView.setOnTouchListener(null)
            return
        }

        try {
            val textElements = mutableListOf<Pair<View, CharSequence>>()
            
            fun traverse(v: View) {
                if (v.visibility != View.VISIBLE) return
                if (v is TextView) {
                    val txt = v.text
                    if (!txt.isNullOrBlank()) textElements.add(Pair(v, txt))
                } else if (GeekReflectionPool.mmNeatTextViewClass?.isInstance(v) == true) {
                    if (neatGetTextMethod == null) {
                        neatGetTextMethod = v.javaClass.methods.firstOrNull { it.returnType == CharSequence::class.java && it.parameterTypes.isEmpty() && it.name != "getContentDescription" }
                    }
                    val txt = neatGetTextMethod?.invoke(v) as? CharSequence
                    if (!txt.isNullOrBlank()) textElements.add(Pair(v, txt))
                }
                if (v is ViewGroup) { for (i in 0 until v.childCount) traverse(v.getChildAt(i)) }
            }
            traverse(itemView)

            if (textElements.isEmpty() && type != MsgConstants.TYPE_VOICE) return

            textElements.forEachIndexed { index, pair ->
                val v = pair.first
                val savedText = GeekConfig.getString("spoof_${msgId}_$index", "")
                if (savedText.isNotEmpty()) {
                    if (v is TextView) v.text = savedText
                    else if (GeekReflectionPool.mmNeatTextViewClass?.isInstance(v) == true) {
                        if (neatSetTextMethod == null) neatSetTextMethod = v.javaClass.methods.firstOrNull { it.parameterTypes.size == 1 && (it.parameterTypes[0] == CharSequence::class.java || it.parameterTypes[0] == String::class.java) && it.name.length <= 4 }
                        try { neatSetTextMethod?.invoke(v, savedText); v.invalidate() } catch (e: Throwable) {}
                    }
                }
            }

            itemView.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val now = System.currentTimeMillis()
                    val lastClickTime = v.getTag(0x7E120003) as? Long ?: 0L
                    
                    if (now - lastClickTime < 300) { 
                        GeekConfig.richHaptic(v, 1)
                        showUniversalSpoofDialog(itemView.context, msgId, textElements, vh, itemView, isSend, talker, type, msgInfo)
                        v.setTag(0x7E120003, 0L)
                        return@setOnTouchListener true
                    }
                    v.setTag(0x7E120003, now)
                }
                false
            }
        } catch (e: Throwable) {
            ChatUIHook.logErrorOnce("ChatUISpoofEngine_Apply", e)
        }
    }

    private fun showUniversalSpoofDialog(context: Context, msgId: Long, elements: List<Pair<View, CharSequence>>, vh: ChatState.GeekViewHolder, itemView: View, isSend: Int, talker: String, type: Int, msgInfo: Any) {
        val dialog = android.app.Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        val dp = context.resources.displayMetrics.density

        val root = FrameLayout(context).apply { setBackgroundColor(Color.parseColor("#66000000")); setOnClickListener { dialog.dismiss() } }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 16f * dp }
            setPadding((20f * dp).toInt(), (20f * dp).toInt(), (20f * dp).toInt(), (20f * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(-1, -2).apply { gravity = Gravity.CENTER; leftMargin = (30f * dp).toInt(); rightMargin = (30f * dp).toInt() }
            setOnClickListener { } 
        }

        panel.addView(TextView(context).apply {
            text = if (type == MsgConstants.TYPE_VOICE) "语音分发面板" else "文本修改器"
            textSize = 16f; setTextColor(Color.parseColor("#333333")); setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, (15f * dp).toInt())
        })

        val scroll = android.widget.ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        val listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        
        var voiceForwardCard: View? = null
        if (type == MsgConstants.TYPE_VOICE) {
            voiceForwardCard = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply { setColor(Color.parseColor("#F4F4F5")); cornerRadius = 10f * dp; setStroke((1f * dp).toInt(), Color.parseColor("#E4E4E7")) }
                setPadding((16f * dp).toInt(), (14f * dp).toInt(), (16f * dp).toInt(), (14f * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12f * dp).toInt(); bottomMargin = (4f * dp).toInt() }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) foreground = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#20000000")), null, null)

                setOnClickListener { view ->
                    GeekConfig.richHaptic(view, 1)
                    val imgPath = try { GeekReflectionPool.fieldImgPath?.get(msgInfo) as? String ?: "" } catch(e:Throwable){ "" }
                    dialog.dismiss()
                    if (imgPath.isNotEmpty()) MessageSender.showForwardDialog(context, imgPath, ChatHook.wechatClassLoader) else GeekConfig.showToast(context, "获取语音路径失败")
                }
            }

            val textLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            textLayout.addView(TextView(context).apply { text = "分发此语音"; textSize = 15f; setTextColor(Color.parseColor("#18181B")); setTypeface(null, Typeface.BOLD) })
            textLayout.addView(TextView(context).apply { text = "转发给好友或群聊"; textSize = 12f; setTextColor(Color.parseColor("#71717A")); setPadding(0, (2f * dp).toInt(), 0, 0) })
            (voiceForwardCard as LinearLayout).addView(textLayout)
        }

        val editInputs = mutableListOf<android.widget.EditText>()
        val nickTv = ChatUIRenderer.getVisibleNickTv(itemView, vh, isSend, talker)
        val nickOriginalCharSeq = nickTv?.text ?: ""; val nickOriginalText = nickOriginalCharSeq.toString()
        var selectedNickColor = 0; var globalNickMode = -1; var hasNickInDialog = false

        elements.forEachIndexed { index, pair ->
            val v = pair.first; val originalCharSeq = pair.second; val originalStr = originalCharSeq.toString()
            val isNick = v === nickTv
            val isBubbleText = v === vh.bubbleView || ChatUIRenderer.isDescendant(vh.bubbleView, v) || GeekReflectionPool.mmNeatTextViewClass?.isInstance(v) == true
            val isTime = v === vh.timeView || (originalStr.length < 25 && originalStr.matches(Regex(".*\\d{1,2}:\\d{2}.*")))
            
            listContainer.addView(TextView(context).apply { text = when { isNick -> "群昵称"; isBubbleText -> "消息内容"; v === vh.revokeHint -> "撤回提示"; isTime -> "消息时间"; else -> "文本元素" }; textSize = 13f; setTextColor(Color.parseColor("#666666")); setTypeface(null, Typeface.BOLD); setPadding((4f * dp).toInt(), (8f * dp).toInt(), 0, (6f * dp).toInt()) })

            val savedText = GeekConfig.getString("spoof_${msgId}_$index", "")
            val input = android.widget.EditText(context).apply {
                setText(if (savedText.isNotEmpty()) savedText else originalCharSeq) 
                textSize = 15f; setTextColor(Color.BLACK); background = GradientDrawable().apply { setColor(Color.parseColor("#F5F5F5")); cornerRadius = 8f * dp; setStroke((1f * dp).toInt(), Color.parseColor("#DDDDDD")) }
                setPadding((12f * dp).toInt(), (10f * dp).toInt(), (12f * dp).toInt(), (10f * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (4f * dp).toInt() }
            }
            editInputs.add(input); listContainer.addView(input)

            if (isNick && originalStr.isNotEmpty()) {
                hasNickInDialog = true; selectedNickColor = GeekConfig.getInt("nick_color_${originalStr.hashCode()}", 0)
                val colorPanel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; background = GradientDrawable().apply { setColor(Color.parseColor("#FAFAFA")); cornerRadius = 8f * dp }; setPadding((10f * dp).toInt(), (10f * dp).toInt(), (10f * dp).toInt(), (10f * dp).toInt()); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (8f * dp).toInt() } }
                colorPanel.addView(TextView(context).apply { text = "设置群昵称专属颜色"; textSize = 12f; setTextColor(Color.parseColor("#888888")); setPadding(0, 0, 0, (8f * dp).toInt()) })

                val colorLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (8f * dp).toInt() } }
                
                val customHexInput = android.widget.EditText(context).apply {
                    hint = "填入 HEX (如 #FF0000)"
                    textSize = 13f
                    setTextColor(Color.parseColor("#333333"))
                    background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 6f * dp; setStroke((1f * dp).toInt(), Color.parseColor("#DDDDDD")) }
                    setPadding((8f * dp).toInt(), (8f * dp).toInt(), (8f * dp).toInt(), (8f * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (12f * dp).toInt() }
                    setSingleLine(true)
                }

                val colors = intArrayOf(Color.parseColor("#E57373"), Color.parseColor("#64B5F6"), Color.parseColor("#81C784"), Color.parseColor("#FFB300"), Color.parseColor("#BA68C8"), Color.parseColor("#90A4AE"), Color.parseColor("#111111"))
                val colorViews = mutableListOf<View>()

                if (selectedNickColor != 0 && !colors.contains(selectedNickColor)) {
                    customHexInput.setText(String.format("#%06X", 0xFFFFFF and selectedNickColor))
                }

                colors.forEach { col ->
                    val outline = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke((2f*dp).toInt(), Color.parseColor("#333333")) }
                    val colorBtn = FrameLayout(context).apply { 
                        layoutParams = LinearLayout.LayoutParams(0, (30f * dp).toInt(), 1f).apply { setMargins((2f*dp).toInt(), 0, (2f*dp).toInt(), 0) }
                        addView(View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col) }; layoutParams = FrameLayout.LayoutParams((24f * dp).toInt(), (24f * dp).toInt()).apply { gravity = Gravity.CENTER } })
                        background = if (col == selectedNickColor) outline else null 
                    }
                    
                    colorBtn.setOnClickListener { view -> 
                        GeekConfig.richHaptic(view, 1)
                        selectedNickColor = col
                        colorViews.forEach { it.background = null }
                        colorBtn.background = outline
                        globalNickMode = -1 
                        
                        val watcher = customHexInput.getTag(0x7E120055) as? android.text.TextWatcher
                        if (watcher != null) customHexInput.removeTextChangedListener(watcher)
                        customHexInput.setText("")
                        if (watcher != null) customHexInput.addTextChangedListener(watcher)
                    }
                    colorViews.add(colorBtn)
                    colorLayout.addView(colorBtn)
                }
                
                val hexWatcher = object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) {
                        val hex = s?.toString()?.trim() ?: ""
                        if (hex.matches(Regex("^#[0-9A-Fa-f]{6}$")) || hex.matches(Regex("^#[0-9A-Fa-f]{8}$"))) {
                            try {
                                selectedNickColor = Color.parseColor(hex)
                                colorViews.forEach { it.background = null }
                                globalNickMode = -1
                            } catch (e: Exception) {}
                        }
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                }
                
                customHexInput.setTag(0x7E120055, hexWatcher)
                customHexInput.addTextChangedListener(hexWatcher)

                colorPanel.addView(colorLayout)
                colorPanel.addView(customHexInput)

                val colorBtnRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2) }
                colorBtnRow.addView(TextView(context).apply { text = "清空"; textSize = 12f; setTextColor(Color.parseColor("#666666")); gravity = Gravity.CENTER; background = GradientDrawable().apply { setColor(Color.parseColor("#EEEEEE")); cornerRadius = 6f * dp }; layoutParams = LinearLayout.LayoutParams(0, (28f * dp).toInt(), 1f).apply { rightMargin = (4f * dp).toInt() }; setOnClickListener { view -> GeekConfig.richHaptic(view, 1); selectedNickColor = 0; colorViews.forEach { it.background = null }; globalNickMode = 0 } })
                colorBtnRow.addView(TextView(context).apply { text = "全员统一"; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; background = GradientDrawable().apply { setColor(Color.parseColor("#FF9800")); cornerRadius = 6f * dp }; layoutParams = LinearLayout.LayoutParams(0, (28f * dp).toInt(), 1.5f).apply { leftMargin = (4f * dp).toInt(); rightMargin = (4f * dp).toInt() }; setOnClickListener { view -> GeekConfig.richHaptic(view, 1); if (selectedNickColor != 0) { globalNickMode = 2; } } })
                colorBtnRow.addView(TextView(context).apply { text = "动态多色"; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; background = GradientDrawable().apply { setColor(Color.parseColor("#BA68C8")); cornerRadius = 6f * dp }; layoutParams = LinearLayout.LayoutParams(0, (28f * dp).toInt(), 1.5f).apply { leftMargin = (4f * dp).toInt() }; setOnClickListener { view -> GeekConfig.richHaptic(view, 1); globalNickMode = 1; } })
                colorPanel.addView(colorBtnRow); listContainer.addView(colorPanel)
            }
        }
        
        if (voiceForwardCard != null) {
            listContainer.addView(voiceForwardCard)
        }
        
        scroll.addView(listContainer)

        val maxH = (context.resources.displayMetrics.heightPixels * 0.55f).toInt()
        val scrollWrapper = object : FrameLayout(context) { override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { super.onMeasure(widthMeasureSpec, if (View.MeasureSpec.getSize(heightMeasureSpec) > maxH) View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST) else heightMeasureSpec) } }
        scrollWrapper.addView(scroll); panel.addView(scrollWrapper)

        panel.addView(TextView(context).apply {
            text = "保存并生效"; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD); background = GradientDrawable().apply { setColor(Color.parseColor("#66B3FF")); cornerRadius = 8f * dp }; layoutParams = LinearLayout.LayoutParams(-1, (42f * dp).toInt()).apply { topMargin = (15f * dp).toInt() }
            setOnClickListener {
                editInputs.forEachIndexed { index, input ->
                    val newText = input.text.toString().trim(); val originalCharSeq = elements[index].second; val originalStr = originalCharSeq.toString()
                    if (newText != originalStr && newText.isNotEmpty()) {
                        GeekConfig.putString("spoof_${msgId}_$index", newText); val v = elements[index].first
                        if (v is TextView) v.text = newText else if (GeekReflectionPool.mmNeatTextViewClass?.isInstance(v) == true) { try { neatSetTextMethod?.invoke(v, newText); v.invalidate() } catch (e: Throwable) {} }
                    } else if (newText.isEmpty() || newText == originalStr) {
                        GeekConfig.putString("spoof_${msgId}_$index", ""); val v = elements[index].first
                        if (v is TextView) v.text = originalCharSeq else if (GeekReflectionPool.mmNeatTextViewClass?.isInstance(v) == true) { try { neatSetTextMethod?.invoke(v, originalCharSeq); v.invalidate() } catch (e: Throwable) {} }
                    }
                }
                if (hasNickInDialog && nickTv != null) {
                    val hash = nickOriginalText.hashCode()
                    when (globalNickMode) { 0 -> { GeekConfig.putInt("key_nickname_color_mode", 0); GeekConfig.putInt("nick_color_$hash", 0) } 1 -> GeekConfig.putInt("key_nickname_color_mode", 1); 2 -> { GeekConfig.putInt("key_nickname_color_mode", 2); GeekConfig.putInt("key_nickname_color_uniform", selectedNickColor) }; -1 -> GeekConfig.putInt("nick_color_$hash", selectedNickColor) }
                    try { (ChatState.currentAdapter as? android.widget.BaseAdapter)?.notifyDataSetChanged() } catch(e: Throwable){}
                }
                dialog.dismiss()
            }
        })
        root.addView(panel); dialog.setContentView(root)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }
}
