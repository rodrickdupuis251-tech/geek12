package com.ljx.wechatmod.hook

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.ljx.wechatmod.core.ChatState
import com.ljx.wechatmod.core.ConfigCache
import com.ljx.wechatmod.core.GeekConfig
import com.ljx.wechatmod.core.GeekLogger
import com.ljx.wechatmod.core.MsgConstants
import com.ljx.wechatmod.core.GeekReflectionPool
import com.ljx.wechatmod.mapper.WeChatVersionMapper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.ConcurrentHashMap

object ChatUIHook {
    private var cachedTransformInterfaceClass: Class<*>? = null
    private var isTransformClassSearched = false
    private var lastSlideEntranceState: Boolean? = null
    private var lastBindItemTime = 0L

    private val errorLoggedMap = ConcurrentHashMap<String, Boolean>()

    fun logErrorOnce(tag: String, e: Throwable) {
        if (errorLoggedMap.putIfAbsent(tag, true) == null) {
            GeekLogger.logError(tag, e)
        }
    }

    fun init(loader: ClassLoader) {
        val adapterName = WeChatVersionMapper.chattingAdapterClass
        val processMethodName = WeChatVersionMapper.chattingAdapterProcessMethod
        if (adapterName.isEmpty() || processMethodName.isEmpty()) return
        
        val adapterClass = XposedHelpers.findClassIfExists(adapterName, loader)
        if (adapterClass != null) {
            GeekConfig.coreHookStatus["ChatUI"] = true 
            try {
                XposedBridge.hookAllMethods(adapterClass, processMethodName, object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) { GeekConfig.safeRun("ChatUI_Before") { preProcessAdapterItem(p) } }
                    override fun afterHookedMethod(p: MethodHookParam) { GeekConfig.safeRun("ChatUI") { processAdapterItem(p) } }
                })
            } catch (e: Throwable) { 
                GeekConfig.coreHookStatus["ChatUI"] = false 
                logErrorOnce("ChatUIHook_Init", e)
            }
        } else GeekConfig.coreHookStatus["ChatUI"] = false
    }

    private fun preProcessAdapterItem(p: XC_MethodHook.MethodHookParam) {
        ConfigCache.updateIfNeed() 
        if (!ConfigCache.isAutoVoiceToText) return
        var position = -1
        if (p.args[0] is Int) position = p.args[0] as Int else if (p.args[1] is Int) position = p.args[1] as Int
        if (position == -1) return
        
        var msgInfo: Any? = null
        if (GeekReflectionPool.cachedGetItemMethod == null) {
            try { GeekReflectionPool.cachedGetItemMethod = XposedHelpers.findMethodBestMatch(p.thisObject.javaClass, WeChatVersionMapper.chattingAdapterGetItemMethod, position)?.apply { isAccessible = true } } catch(e: Throwable){ logErrorOnce("ChatUIHook_Reflect", e) }
        }
        try { msgInfo = GeekReflectionPool.cachedGetItemMethod?.invoke(p.thisObject, position) } catch(e: Throwable){ logErrorOnce("ChatUIHook_Invoke", e) }
        if (msgInfo == null) return

        GeekReflectionPool.bindMsgFields(msgInfo)
        val type = GeekReflectionPool.fieldType?.getInt(msgInfo) ?: 0
        val isSend = GeekReflectionPool.fieldIsSend?.getInt(msgInfo) ?: 0

        if (type == MsgConstants.TYPE_VOICE && isSend == 0) {
            val msgId = GeekReflectionPool.fieldMsgId?.getLong(msgInfo) ?: 0L
            if (msgId == 0L) return

            var isAlreadyExpanded = false; var msgStatus = 0
            try {
                isAlreadyExpanded = ((XposedHelpers.getIntField(msgInfo, WeChatVersionMapper.msgFlagField) as Int) and 64) != 0
                msgStatus = XposedHelpers.getIntField(msgInfo, WeChatVersionMapper.msgStatusField) as Int
            } catch (e: Throwable) { logErrorOnce("ChatUIHook_VoiceState", e) }

            if (msgStatus >= 3) {
                if (GeekConfig.getBoolean("voice_trans_$msgId", false) || isAlreadyExpanded) return 
                if (ChatState.pendingVoiceTrans[msgId] != true) {
                    ChatState.pendingVoiceTrans[msgId] = true
                    try {
                        if (!isTransformClassSearched) { isTransformClassSearched = true; cachedTransformInterfaceClass = XposedHelpers.findClassIfExists(WeChatVersionMapper.uiClassTransformInterface, ChatHook.wechatClassLoader) }
                        if (cachedTransformInterfaceClass != null) {
                            val chattingContext = XposedHelpers.getObjectField(p.thisObject, WeChatVersionMapper.adapterChattingContextField)
                            val componentMgr = chattingContext?.let { XposedHelpers.getObjectField(it, WeChatVersionMapper.contextComponentMgrField) }
                            val transformComponent = componentMgr?.let { XposedHelpers.callMethod(it, WeChatVersionMapper.componentMgrGetMethod, cachedTransformInterfaceClass) }
                            if (transformComponent != null) {
                                val methodName = WeChatVersionMapper.transformMethodName
                                try { XposedHelpers.callMethod(transformComponent, methodName, msgInfo, true, position, 0) } catch (e: Throwable) { try { XposedHelpers.callMethod(transformComponent, methodName, msgId) } catch (e2: Throwable) { logErrorOnce("ChatUIHook_Transform", e2) } }
                            }
                        }
                    } catch (e: Throwable) { ChatState.pendingVoiceTrans.remove(msgId); logErrorOnce("ChatUIHook_VoiceTrigger", e) }
                }
            }
        }
    }

    private fun processAdapterItem(p: XC_MethodHook.MethodHookParam) {
        ConfigCache.updateIfNeed() 
        ChatState.currentAdapter = p.thisObject

        if (lastSlideEntranceState != ConfigCache.isSlideEntranceOn) { 
            lastSlideEntranceState = ConfigCache.isSlideEntranceOn
        }

        var holder: Any? = null; var position = -1
        if (p.args[0] is Int) { position = p.args[0] as Int; holder = p.args[1] } else if (p.args[1] is Int) { position = p.args[1] as Int; holder = p.args[0] }
        if (holder == null || position == -1) return
        
        var msgInfo: Any? = null
        if (GeekReflectionPool.cachedGetItemMethod == null) {
            try { GeekReflectionPool.cachedGetItemMethod = XposedHelpers.findMethodBestMatch(p.thisObject.javaClass, WeChatVersionMapper.chattingAdapterGetItemMethod, position)?.apply { isAccessible = true } } catch(e: Throwable){}
        }
        try { msgInfo = GeekReflectionPool.cachedGetItemMethod?.invoke(p.thisObject, position) } catch(e: Throwable){}
        if (msgInfo == null) return

        GeekReflectionPool.bindMsgFields(msgInfo)
        val timeMethod = WeChatVersionMapper.msgCreateTimeMethod
        val time = if (timeMethod.isNotEmpty()) { try { XposedHelpers.callMethod(msgInfo, timeMethod) as Long } catch (e: Throwable) { 0L } } else 0L
        val isSend = GeekReflectionPool.fieldIsSend?.getInt(msgInfo) ?: 0
        val talker = GeekReflectionPool.fieldTalker?.get(msgInfo) as? String ?: ""
        val msgId = GeekReflectionPool.fieldMsgId?.getLong(msgInfo) ?: 0L
        val type = GeekReflectionPool.fieldType?.getInt(msgInfo) ?: 0

        var itemView: View? = extractItemView(holder) ?: holder as? View
        if (itemView == null) return

        var vh = itemView.getTag(ChatState.GEEK_TAG_ID) as? ChatState.GeekViewHolder
        if (vh == null) { vh = ChatState.GeekViewHolder(); itemView.setTag(ChatState.GEEK_TAG_ID, vh) }
        vh.msgData = if (ConfigCache.isSwipeOn && type != MsgConstants.TYPE_RECALLED) ChatState.MsgData(msgInfo, type, talker, msgId, isSend) else null

        ChatUIRenderer.scanViewNodes(itemView, vh, type)
        ChatUIRenderer.applyBubbleStyle(itemView, vh, type, isSend)
        ChatUIRenderer.applyAvatarAndTime(itemView, vh, isSend, talker, time, ConfigCache.isHideSelfAvatar, ConfigCache.isHideFriendAvatar, ConfigCache.isShowMsgTime)
        ChatUIRenderer.applyRecallHint(itemView, vh, msgId, ConfigCache.isRecallOn)
        ChatUISpoofEngine.applyUniversalSpoof(itemView, vh, type, msgId, msgInfo, isSend, talker)
        ChatUIRenderer.applyColorfulNickname(itemView, vh, isSend, talker, msgInfo)

        val adapterHash = System.identityHashCode(p.thisObject)
        val now = System.currentTimeMillis()
        
        if (ChatState.lastAdapterHash != adapterHash || now - lastBindItemTime > 60000L) {
            ChatState.lastAdapterHash = adapterHash; ChatState.chatBoxEnterTime = now
        }
        lastBindItemTime = now 

        val oldMsgId = itemView.getTag(0x7E060011) as? Long ?: -1L
        itemView.setTag(0x7E060011, msgId)

        var shouldAnimate = false

        if (ConfigCache.isChatAnimOn) {
            val realTime = if (time in 100000000L..9999999999L) time * 1000L else time
            val isRecentNewMsg = (now - realTime) < 2000L
            
            if (isRecentNewMsg) {
                itemView.rootView.setTag(0x7E120099, now)
            }

            val isViewRecycled = (oldMsgId != msgId)

            if (!isViewRecycled) {
                shouldAnimate = false
            } else {
                if (isRecentNewMsg) {
                    shouldAnimate = true
                } else {
                    if (ConfigCache.isBounceAllOnEnter) {
                        val lastNewMsgTime = itemView.rootView.getTag(0x7E120099) as? Long ?: 0L
                        val isPushedByNewMsg = (now - lastNewMsgTime < 500L)
                        
                        if (isPushedByNewMsg) {
                            shouldAnimate = false 
                        } else {
                            shouldAnimate = true  
                        }
                    } else {
                        shouldAnimate = false
                    }
                }
            }
        }

        if (shouldAnimate) {
            GestureHook.hardResetForRecycle(itemView, vh)
            (itemView.getTag(0x7E070001) as? SpringAnimation)?.apply { if (isRunning) cancel() }
            (itemView.getTag(0x7E070002) as? SpringAnimation)?.apply { if (isRunning) cancel() }
            (itemView.getTag(0x7E070003) as? SpringAnimation)?.apply { if (isRunning) cancel() }
            (itemView.getTag(0x7E080001) as? SpringAnimation)?.apply { if (isRunning) cancel() }
            
            itemView.translationX = 0f; itemView.translationY = 0f; itemView.rotation = 0f; itemView.rotationX = 0f; itemView.rotationY = 0f
            itemView.scaleX = 1f; itemView.scaleY = 1f; itemView.alpha = 0f
            
            if (ConfigCache.isSlideEntranceOn) {
                itemView.setTag(0x7E060008, msgId); itemView.setTag(0x7E060009, null) 
                val slideDistance = 120f * itemView.context.resources.displayMetrics.density
                val startX = if (isSend == 1) slideDistance else -slideDistance
                try { WaterfallEngine.requestAnim(itemView, position, startX, msgId) } 
                catch (e: Throwable) { itemView.animate().alpha(1f).setDuration(250).start() }
            } else {
                itemView.setTag(0x7E060008, null); val animToken = System.nanoTime(); itemView.setTag(0x7E060009, animToken)
                itemView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                itemView.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        itemView.viewTreeObserver.removeOnPreDrawListener(this)
                        if ((itemView.getTag(0x7E060009) as? Long) != animToken) return true
                        itemView.animate().setListener(null).withEndAction(null).alpha(1f).setDuration(250).start()
                        val enterSpringX = itemView.getTag(0x7E080001) as? SpringAnimation ?: SpringAnimation(itemView, DynamicAnimation.SCALE_X).apply { spring = SpringForce(); setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE); addUpdateListener { _, value, _ -> itemView.scaleY = value } }.also { itemView.setTag(0x7E080001, it) }
                        itemView.scaleX = 0.85f; itemView.scaleY = 0.85f
                        enterSpringX.spring?.setFinalPosition(1f)?.setStiffness(300f)?.setDampingRatio(0.6f)
                        enterSpringX.addEndListener(object : DynamicAnimation.OnAnimationEndListener { override fun onAnimationEnd(animation: DynamicAnimation<out DynamicAnimation<*>>?, canceled: Boolean, value: Float, velocity: Float) { itemView.setLayerType(View.LAYER_TYPE_NONE, null); enterSpringX.removeEndListener(this) } })
                        enterSpringX.start()
                        return true
                    }
                })
            }
        } else resetItemViewState(itemView, msgId, vh) 
    }

    private fun resetItemViewState(itemView: View, msgId: Long, vh: ChatState.GeekViewHolder?) {
        (itemView.getTag(0x7E070001) as? SpringAnimation)?.apply { if (isRunning) cancel() }
        (itemView.getTag(0x7E070002) as? SpringAnimation)?.apply { if (isRunning) cancel() }
        (itemView.getTag(0x7E070003) as? SpringAnimation)?.apply { if (isRunning) cancel() }
        (itemView.getTag(0x7E080001) as? SpringAnimation)?.apply { if (isRunning) cancel() }
        itemView.setTag(0x7E060008, null); itemView.setTag(0x7E060009, null)
        GestureHook.hardResetForRecycle(itemView, vh)
    }

    private fun extractItemView(holder: Any): View? {
        if (GeekReflectionPool.cachedItemViewField != null) { try { return GeekReflectionPool.cachedItemViewField!!.get(holder) as? View } catch(e: Throwable){} } 
        else if (!GeekReflectionPool.isItemViewFieldSearched) {
            GeekReflectionPool.isItemViewFieldSearched = true
            try { val f = XposedHelpers.findFieldIfExists(holder.javaClass, "itemView"); if (f != null) { f.isAccessible = true; GeekReflectionPool.cachedItemViewField = f; return f.get(holder) as? View } } catch(e: Throwable){}
        }
        return null
    }
}
