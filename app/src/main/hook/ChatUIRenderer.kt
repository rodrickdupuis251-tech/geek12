package com.ljx.wechatmod.hook

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.ljx.wechatmod.auth.CloudTagManager
import com.ljx.wechatmod.core.ChatState
import com.ljx.wechatmod.core.ConfigCache
import com.ljx.wechatmod.core.GeekConfig
import com.ljx.wechatmod.core.MsgConstants
import com.ljx.wechatmod.core.GeekReflectionPool
import com.ljx.wechatmod.mapper.WeChatVersionMapper
import com.ljx.wechatmod.ui.GeekTagSpan
import com.ljx.wechatmod.ui.RealNameSpan
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object ChatUIRenderer {
    private val colorCache = ConcurrentHashMap<String, Int>()
    private var cachedDensity = -1f
    private var r = 0f; private var smallR = 0f; private var padH = 0; private var padV = 0

    private val nameColors = intArrayOf(
        Color.parseColor("#E57373"), Color.parseColor("#F06292"), Color.parseColor("#BA68C8"),
        Color.parseColor("#9575CD"), Color.parseColor("#7986CB"), Color.parseColor("#64B5F6"),
        Color.parseColor("#4FC3F7"), Color.parseColor("#4DD0E1"), Color.parseColor("#4DB6AC"),
        Color.parseColor("#81C784"), Color.parseColor("#AED581"), Color.parseColor("#FF8A65")
    )

    private fun getSafeColor(hex: String): Int = colorCache.getOrPut(hex) { Color.parseColor(hex) }
    private fun isNightMode(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES else false

    private fun updateDensityCache(context: Context) {
        val currentDensity = context.resources.displayMetrics.density
        if (cachedDensity != currentDensity) {
            cachedDensity = currentDensity; r = 13f * currentDensity; smallR = 4f * currentDensity; padH = (11f * currentDensity).toInt(); padV = (8f * currentDensity).toInt()
        }
    }

    private fun getFriendThemeColors(idx: Int, isNight: Boolean): Pair<Int, Int> {
        val starts = arrayOf("#A9EA7A", "#73C5FF", "#6BB8FF", "#859BE8", "#A9A0DF", "#CD9BEE", "#FFB1C8", "#FF8C82", "#FFC073", "#FFD54F", "#AED581")
        val ends = arrayOf("#1AAD19", "#1296DB", "#005AD4", "#3F51B5", "#5856D6", "#AF52DE", "#FF2D55", "#FF3B30", "#FF9500", "#FFB300", "#8BC34A")
        val nightStarts = arrayOf("#2A4D1A", "#184568", "#123A6D", "#212A54", "#2B2857", "#44225C", "#661222", "#661814", "#663B00", "#664B00", "#334D1A")
        val nightEnds = arrayOf("#0A3D09", "#08334E", "#001D4D", "#111833", "#1B1A3B", "#341844", "#4D0E19", "#4D120E", "#4D2D00", "#4D3800", "#223D0E")

        val safeIdx = idx.coerceIn(0, 10)
        return if (isNight) {
            Pair(Color.parseColor(nightStarts[safeIdx]), Color.parseColor(nightEnds[safeIdx]))
        } else {
            Pair(Color.parseColor(starts[safeIdx]), Color.parseColor(ends[safeIdx]))
        }
    }

    fun scanViewNodes(itemView: View, vh: ChatState.GeekViewHolder, type: Int) {
        if (vh.isScanned || itemView !is ViewGroup) return
        fun traverse(v: View) {
            if (v is ImageView) {
                if (vh.avatarView == null) {
                    val lp = v.layoutParams
                    if (lp != null && lp.width > 0 && lp.height == lp.width) {
                        val widthDp = (lp.width / v.resources.displayMetrics.density).toInt()
                        if (widthDp in 35..65) { vh.avatarView = v; v.setTag(0x7E0B0000, true) }
                    }
                }
                if (type == MsgConstants.TYPE_VOICE && v.background == null && v !== vh.avatarView && (v.layoutParams?.width ?: 0) < 100) vh.voiceWaveImage = v
            } else if (v is TextView) {
                if (type == MsgConstants.TYPE_VOICE && v.text.toString().contains("\"")) vh.voiceDurationText = v
            }
            
            if (vh.bubbleView == null) {
                try {
                    if (v.id != View.NO_ID) {
                        val resName = v.context.resources.getResourceEntryName(v.id)
                        if (resName == "chatting_click_area" || resName == "chatting_bg_view") {
                            vh.bubbleView = v
                        }
                    }
                } catch (e: Throwable) {}
                
                if (vh.bubbleView == null) {
                    if (type == MsgConstants.TYPE_TEXT || type == MsgConstants.TYPE_APP_MSG_LUCKY) {
                        if (GeekReflectionPool.mmNeatTextViewClass != null) {
                            if (GeekReflectionPool.mmNeatTextViewClass!!.isAssignableFrom(v.javaClass)) vh.bubbleView = v
                        } else if (v.javaClass.name.endsWith(WeChatVersionMapper.uiClassMMNeatTextView)) { 
                            vh.bubbleView = v; GeekReflectionPool.mmNeatTextViewClass = v.javaClass 
                        }
                    } else if (type == MsgConstants.TYPE_VOICE) {
                        if (v !== itemView && v.background != null && v is ViewGroup) vh.bubbleView = v
                    }
                }
            }
            if (v is ViewGroup) { for (i in 0 until v.childCount) traverse(v.getChildAt(i)) }
        }
        traverse(itemView)
        
        if (vh.nickNameView == null) {
            val targetResName = WeChatVersionMapper.chattingPrivacyResNames.getOrNull(2) ?: "chatting_user_tv"
            val pkgName = itemView.context.packageName
            var resId = itemView.context.resources.getIdentifier(targetResName, "id", pkgName)
            if (resId == 0) { try { val rIdClass = XposedHelpers.findClass("$pkgName.R\$id", itemView.context.classLoader); resId = XposedHelpers.getStaticIntField(rIdClass, targetResName) } catch (e: Throwable) { } }
            if (resId != 0) vh.nickNameView = itemView.findViewById(resId)
        }
        val parent = vh.avatarView?.parent as? ViewGroup
        if (parent != null && vh.timeView == null) {
            val density = itemView.context.resources.displayMetrics.density
            vh.timeView = TextView(itemView.context).apply { textSize = 10f; setTextColor(Color.WHITE); setShadowLayer(3f, 0f, 1f, Color.parseColor("#80000000")); setBackgroundColor(Color.TRANSPARENT); background = null; gravity = Gravity.CENTER; visibility = View.GONE }
            if (parent is FrameLayout) parent.addView(vh.timeView, FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = (2f * density).toInt() }) 
            else if (parent is RelativeLayout) parent.addView(vh.timeView, RelativeLayout.LayoutParams(-2, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); addRule(RelativeLayout.CENTER_HORIZONTAL); bottomMargin = (2f * density).toInt() }) 
            else if (parent is LinearLayout) parent.addView(vh.timeView, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = (2f * density).toInt() }) 
            else parent.addView(vh.timeView,  ViewGroup.MarginLayoutParams(-2, -2))
        }
        vh.isScanned = true
    }

    fun applyBubbleStyle(itemView: View, vh: ChatState.GeekViewHolder, type: Int, isSend: Int) {
        if (!ConfigCache.isCustomBubble || vh.bubbleView == null) return

        if (type == MsgConstants.TYPE_VOICE) return 

        val context = itemView.context; updateDensityCache(context)
        
        if (type == MsgConstants.TYPE_TEXT || type == MsgConstants.TYPE_APP_MSG_LUCKY) {
            applyJellyEffect(vh.bubbleView!!)
        }

        val isNight = isNightMode(context)
        val myThemeIdx = ConfigCache.bubbleThemeIdx
        val friendThemeIdx = GeekConfig.getInt("key_friend_bubble_theme", 0)

        val startColor: Int
        val endColor: Int

        if (isSend == 1) {
            startColor = getSafeColor(if (isNight) ConfigCache.bubbleColorStartNight else ConfigCache.bubbleColorStart)
            endColor = getSafeColor(if (isNight) ConfigCache.bubbleColorEndNight else ConfigCache.bubbleColorEnd)
        } else {
            if (friendThemeIdx > 0) {
                val colors = getFriendThemeColors(friendThemeIdx - 1, isNight)
                startColor = colors.first
                endColor = colors.second
            } else {
                startColor = if (isNight) getSafeColor(MsgConstants.COLOR_NIGHT_RECV) else getSafeColor(MsgConstants.COLOR_DAY_RECV)
                endColor = startColor
            }
        }

        if (type == MsgConstants.TYPE_TEXT || type == MsgConstants.TYPE_APP_MSG_LUCKY) {
            val cacheKey = "bg_${isSend}_${isNight}_${myThemeIdx}_${friendThemeIdx}" 
            var cachedState = ChatState.bgCache[cacheKey]
            
            if (cachedState == null) {
                val bg = GradientDrawable()
                if (isSend == 1) { 
                    bg.cornerRadii = floatArrayOf(r, r, r, r, smallR, smallR, r, r)
                    bg.colors = intArrayOf(startColor, endColor)
                    bg.orientation = GradientDrawable.Orientation.TL_BR 
                } else { 
                    if (friendThemeIdx > 0) {
                        bg.cornerRadii = floatArrayOf(smallR, smallR, r, r, r, r, r, r)
                        bg.colors = intArrayOf(startColor, endColor)
                        bg.orientation = GradientDrawable.Orientation.TR_BL 
                    } else {
                        bg.cornerRadii = floatArrayOf(smallR, smallR, r, r, r, r, r, r)
                        bg.setColor(startColor)
                        if (!isNight) bg.setStroke(1, getSafeColor(MsgConstants.COLOR_DAY_STROKE)) 
                    }
                }
                val ripple = RippleDrawable(ColorStateList.valueOf(getSafeColor(if(isNight) "#33FFFFFF" else "#22000000")), bg, null)
                cachedState = ripple.constantState; if (cachedState != null) ChatState.bgCache[cacheKey] = cachedState
            }
            vh.bubbleView?.background = cachedState?.newDrawable()?.mutate()
            
            val targetTextColor = if (isSend == 1 || friendThemeIdx > 0) Color.WHITE else (if (isNight) Color.WHITE else Color.BLACK)
            try { XposedHelpers.callMethod(vh.bubbleView, "setTextColor", targetTextColor) } catch(e: Throwable){}
            
            vh.bubbleView?.setPadding(padH, padV, padH, padV)
        }
    }

    fun applyAvatarAndTime(itemView: View, vh: ChatState.GeekViewHolder, isSend: Int, talker: String, time: Long, isHideSelfOn: Boolean, isHideFriendOn: Boolean, isTimeOn: Boolean) {
        vh.avatarView?.let { av ->
            val hide = if (isSend == 1) isHideSelfOn else (!talker.endsWith("@chatroom") && isHideFriendOn)
            if (hide) { 
                av.visibility = View.GONE; (av.parent as? View)?.visibility = View.GONE 
            } else {
                av.visibility = View.VISIBLE; (av.parent as? View)?.visibility = View.VISIBLE
            }
            av.setOnTouchListener { v, event ->
                val springX = v.getTag(0x7E090004) as? SpringAnimation ?: SpringAnimation(v, DynamicAnimation.SCALE_X).apply { spring = SpringForce(); setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE) }.also { v.setTag(0x7E090004, it) }
                val springY = v.getTag(0x7E090005) as? SpringAnimation ?: SpringAnimation(v, DynamicAnimation.SCALE_Y).apply { spring = SpringForce(); setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE) }.also { v.setTag(0x7E090005, it) }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { springX.spring?.setFinalPosition(0.85f)?.setStiffness(SpringForce.STIFFNESS_MEDIUM)?.setDampingRatio(0.85f); springY.spring?.setFinalPosition(0.85f)?.setStiffness(SpringForce.STIFFNESS_MEDIUM)?.setDampingRatio(0.85f); springX.start(); springY.start() }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { springX.spring?.setFinalPosition(1f)?.setStiffness(SpringForce.STIFFNESS_LOW)?.setDampingRatio(0.4f); springY.spring?.setFinalPosition(1f)?.setStiffness(SpringForce.STIFFNESS_LOW)?.setDampingRatio(0.4f); springX.start(); springY.start() }
                }
                false
            }
        }
        if (isTimeOn && vh.avatarView?.visibility == View.VISIBLE && time > 0) { ChatState.sharedDate.time = time; vh.timeView?.text = ChatState.timeFormatter.get()?.format(ChatState.sharedDate); vh.timeView?.visibility = View.VISIBLE } else vh.timeView?.visibility = View.GONE
    }

    fun applyRecallHint(itemView: View, vh: ChatState.GeekViewHolder, msgId: Long, isRecallOn: Boolean) {
        if (itemView !is ViewGroup) return
        var isRevoked = ChatState.revokedMsgs[msgId]; if (isRevoked == null) { isRevoked = GeekConfig.getBoolean(msgId.toString(), false); ChatState.revokedMsgs[msgId] = isRevoked }
        if (isRecallOn && msgId != 0L && isRevoked == true) {
            if (vh.revokeHint == null) {
                val density = itemView.context.resources.displayMetrics.density
                vh.revokeHint = TextView(itemView.context).apply { textSize = 11f; setTextColor(Color.WHITE); typeface = Typeface.defaultFromStyle(Typeface.BOLD); setPadding((8f * density).toInt(), (3f * density).toInt(), (8f * density).toInt(), (3f * density).toInt()); gravity = Gravity.CENTER; background = GradientDrawable().apply { setColor(Color.parseColor("#40000000")); cornerRadius = 6f * density } }
                if (itemView is RelativeLayout) itemView.addView(vh.revokeHint, RelativeLayout.LayoutParams(-2, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); addRule(RelativeLayout.CENTER_HORIZONTAL); bottomMargin = (12f * density).toInt() }) else itemView.addView(vh.revokeHint)
            }
            vh.revokeHint?.text = ConfigCache.recallText; vh.revokeHint?.visibility = View.VISIBLE
        } else vh.revokeHint?.visibility = View.GONE
    }

    private fun applyJellyEffect(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        try {
            view.stateListAnimator = null
            view.setOnTouchListener { v, event ->
                val springX = v.getTag(0x7E060001) as? SpringAnimation ?: SpringAnimation(v, DynamicAnimation.SCALE_X).apply { setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE); spring = SpringForce() }.also { v.setTag(0x7E060001, it) }
                val springY = v.getTag(0x7E060002) as? SpringAnimation ?: SpringAnimation(v, DynamicAnimation.SCALE_Y).apply { setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE); spring = SpringForce() }.also { v.setTag(0x7E060002, it) }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val targetScale = Math.max(0.6f, 1.0f - (0.12f * (ConfigCache.jellyIntensity / 100f)))
                        springX.spring?.setStiffness(SpringForce.STIFFNESS_MEDIUM)?.setDampingRatio(0.6f)?.setFinalPosition(targetScale)
                        springY.spring?.setStiffness(SpringForce.STIFFNESS_MEDIUM)?.setDampingRatio(0.6f)?.setFinalPosition(targetScale)
                        springX.start(); springY.start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        springX.spring?.setStiffness(SpringForce.STIFFNESS_MEDIUM)?.setDampingRatio(0.25f)?.setFinalPosition(1f)
                        springY.spring?.setStiffness(SpringForce.STIFFNESS_MEDIUM)?.setDampingRatio(0.25f)?.setFinalPosition(1f)
                        springX.start(); springY.start()
                    }
                }
                false 
            }
        } catch (e: Throwable) { ChatUIHook.logErrorOnce("ChatUIRenderer_Jelly", e) }
    }

    fun isDescendant(parent: View?, child: View): Boolean {
        var p = child.parent as? View
        while (p != null) { if (p === parent) return true; p = p.parent as? View }
        return false
    }

    fun getVisibleNickTv(itemView: View, vh: ChatState.GeekViewHolder, isSend: Int, talker: String): TextView? {
        if (isSend == 1 || !talker.endsWith("@chatroom")) return null
        
        if (vh.nickNameView is TextView && vh.nickNameView?.visibility == View.VISIBLE) {
            return vh.nickNameView as TextView
        }
        
        var targetId = vh.nickNameView?.id ?: View.NO_ID
        if (targetId == View.NO_ID) targetId = itemView.context.resources.getIdentifier(WeChatVersionMapper.chattingPrivacyResNames.getOrNull(2) ?: "chatting_user_tv", "id", itemView.context.packageName)
        
        var result: TextView? = null
        val candidates = mutableListOf<TextView>()
        
        fun findVisible(v: View) {
            if (v.visibility != View.VISIBLE) return
            
            if (v is TextView && v.text.isNotEmpty()) {
                val textStr = v.text.toString()
                
                if (v.id != View.NO_ID && v.id == targetId) { result = v; return }
                try { 
                    val resName = v.context.resources.getResourceEntryName(v.id)
                    if (resName.contains("chatting_user_tv") || resName.contains("chatting_user_tv_left")) { result = v; return } 
                } catch (e: Throwable) {}
                
                if (v !== vh.timeView && v !== vh.revokeHint && !textStr.matches(Regex(".*\\d{1,2}:\\d{2}.*"))) {
                    if (!(v === vh.bubbleView || isDescendant(vh.bubbleView, v) || GeekReflectionPool.mmNeatTextViewClass?.isInstance(v) == true)) {
                        candidates.add(v)
                    }
                }
            }
            if (v is ViewGroup) { 
                for (i in 0 until v.childCount) { 
                    findVisible(v.getChildAt(i))
                    if (result != null) return 
                } 
            }
        }
        findVisible(itemView)
        
        if (result == null) {
            for (c in candidates) { 
                if (!c.text.toString().matches(Regex(".*\\d{1,2}:\\d{2}.*"))) {
                    result = c
                    break
                } 
            }
            if (result == null) result = candidates.firstOrNull() 
        }

        if (result != null) vh.nickNameView = result
        
        return result
    }

    fun applyColorfulNickname(itemView: View, vh: ChatState.GeekViewHolder, isSend: Int, talker: String, msgInfo: Any?) {
        val nickTv = getVisibleNickTv(itemView, vh, isSend, talker) ?: return

        var senderWxid = talker
        if (isSend == 1) {
            senderWxid = talker
        } else if (talker.endsWith("@chatroom") && msgInfo != null) {
            val rawContent = try {
                XposedHelpers.getObjectField(msgInfo, "field_content") as? String ?: ""
            } catch(e: Throwable) {
                GeekReflectionPool.fieldContent?.get(msgInfo) as? String ?: ""
            }
            
            val sepIndex = rawContent.indexOf(":\n")
            if (sepIndex > 0) {
                senderWxid = rawContent.substring(0, sepIndex).trim()
            } else if (rawContent.contains(":")) {
                val colonIndex = rawContent.indexOf(":")
                if (colonIndex > 0) {
                    senderWxid = rawContent.substring(0, colonIndex).trim()
                }
            }
        }

        if (senderWxid == talker || senderWxid.isEmpty() || senderWxid.contains("<")) return
        nickTv.setTag(0x7E120021, senderWxid) 

        fun applyColors(tv: TextView, cleanName: String) {
            val globalMode = GeekConfig.getInt("key_nickname_color_mode", 0) 
            val uniformColor = GeekConfig.getInt("key_nickname_color_uniform", Color.parseColor("#E57373"))
            val individualColor = GeekConfig.getInt("nick_color_${cleanName.hashCode()}", 0)
            val origColor = tv.getTag(0x7E120020) as? Int ?: tv.currentTextColor
            tv.setTag(0x7E120020, origColor)

            if (individualColor != 0) { tv.setTextColor(individualColor); tv.setTypeface(null, Typeface.BOLD) } 
            else if (globalMode == 1) { tv.setTextColor(nameColors[Math.abs(cleanName.hashCode()) % nameColors.size]); tv.setTypeface(null, Typeface.BOLD) } 
            else if (globalMode == 2) { tv.setTextColor(uniformColor); tv.setTypeface(null, Typeface.BOLD) } 
            else { tv.setTextColor(origColor); tv.setTypeface(null, Typeface.NORMAL) }
        }

        val renderUISync = fun(realName: String) {
            if (nickTv.getTag(0x7E120021) != senderWxid) return

            val currentText = nickTv.text.toString()
            if (currentText.isEmpty()) return

            val cleanName = currentText
                .replace("\u200B", "") 
                .replace(Regex(" \\(\\*.*?\\)"), "") 
                .trim()

            val ssb = SpannableStringBuilder(cleanName)

            if (realName.isNotEmpty()) {
                val formattedRealName = if (realName.startsWith("*")) realName else "*$realName"
                ssb.append(" ") 
                val startIdx = ssb.length
                ssb.append("\u200B") 
                ssb.setSpan(RealNameSpan(formattedRealName), startIdx, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val cloudTagData = CloudTagManager.userTags[senderWxid]
            if (cloudTagData != null) {
                ssb.append(" ") 
                val startIdx = ssb.length
                ssb.append("\u200B") 
                
                ssb.setSpan(GeekTagSpan(cloudTagData.text, cloudTagData.bgColor, cloudTagData.textColor, cloudTagData.isDynamic, nickTv), startIdx, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            nickTv.text = ssb
            applyColors(nickTv, cleanName)
        }

        if (GeekConfig.getBoolean("key_show_real_name", false) && isSend == 0 && talker.endsWith("@chatroom")) {
            val cachedName = GeekConfig.getString("real_name_$senderWxid", "")
            if (cachedName.isEmpty()) {
                renderUISync("") 
                ChatHook.wechatClassLoader?.let { loader ->
                    CoroutineScope(Dispatchers.IO).launch {
                        RealNameHook.fetchRealNameProbe(senderWxid, talker, loader) { fetchedName ->
                            ChatHook.mainHandler.post {
                                renderUISync(fetchedName)
                            }
                        }
                    }
                }
            } else {
                renderUISync(cachedName) 
            }
        } else {
            renderUISync("") 
        }
    }
}
