package io.github.soclear.edgex.hook

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.soclear.edgex.hook.util.afterAttach


object Ui {

    /**
     * 移除 padding
     */
    fun removePadding(
        top: Boolean,
        topPaddingDp: Int = 0,
        bottom: Boolean,
        bottomPaddingDp: Int = 0
    ) {
        if (!top && !bottom) return
        XposedHelpers.findAndHookMethod(
            View::class.java,
            "setPadding",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? View ?: return
                    if (view.javaClass.name == "org.chromium.ui.edge_to_edge.layout.EdgeToEdgeBaseLayout") {
                        val density = view.resources.displayMetrics.density
                        if (top) {
                            param.args[1] = (topPaddingDp * density).toInt().coerceAtLeast(0)
                        }
                        if (bottom) {
                            param.args[3] = (bottomPaddingDp * density).toInt().coerceAtLeast(0)
                        }
                    }
                }
            }
        )
    }

    /**
     * 设置新标签页 URL
     */
    fun setNewTabPageUrl(customUrl: String) = afterAttach {
        val loadUrlParamsClass = XposedHelpers.findClassIfExists(
            "org.chromium.content_public.browser.LoadUrlParams",
            classLoader
        ) ?: run {
            XposedBridge.log("[EdgeX][Ui] 未找到 LoadUrlParams 类")
            return@afterAttach
        }

        val gurlClass = XposedHelpers.findClassIfExists(
            "org.chromium.url.GURL",
            classLoader
        )

        fun isNtpUrl(url: String?): Boolean {
            return url == "chrome-native://newtab/" ||
                url == "edge://newtab/" ||
                url == "chrome://newtab/"
        }

        XposedBridge.hookAllConstructors(loadUrlParamsClass, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                for (i in param.args.indices) {
                    val arg = param.args[i] ?: continue
                    if (arg is String) {
                        if (isNtpUrl(arg)) {
                            param.args[i] = customUrl
                        }
                    } else if (gurlClass != null && gurlClass.isInstance(arg)) {
                        val spec = try {
                            XposedHelpers.getObjectField(arg, "a") as? String
                        } catch (_: Throwable) {
                            null
                        } ?: arg.toString()
                        if (isNtpUrl(spec)) {
                            try {
                                param.args[i] = XposedHelpers.newInstance(gurlClass, customUrl)
                            } catch (t: Throwable) {
                                XposedBridge.log(t)
                            }
                        }
                    }
                }
            }
        })
    }

    /**
     * 隐藏状态栏（沉浸）
     */
    fun hideStatusBar() {
        val hookLifecycle = object : XC_MethodHook() {
            @Suppress("DEPRECATION")
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                val window = activity.window ?: return
                val decorView = window.decorView

                // 兼容 Android 11+ 新版 API (彻底沉浸)
                window.setDecorFitsSystemWindows(false)
                val controller = window.insetsController
                // 隐藏状态栏
                controller?.hide(WindowInsets.Type.statusBars())
                // 隐藏状态栏和导航栏
//                controller?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    .or(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                    .or(View.SYSTEM_UI_FLAG_FULLSCREEN)
                    .or(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY )
//                隐藏导航栏
//                    .or(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
//                    .or(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            }
        }
        // Hook onCreate 和 onResume，防止 Edge 在后续流程中把状态栏拉出来
        XposedHelpers.findAndHookMethod(Activity::class.java, "onCreate", Bundle::class.java, hookLifecycle)
        XposedHelpers.findAndHookMethod(Activity::class.java, "onResume", hookLifecycle)
        XposedHelpers.findAndHookMethod(Activity::class.java, "onWindowFocusChanged", Boolean::class.javaPrimitiveType, hookLifecycle)
    }


    /**
     * 透明状态栏（保留图标与时间，沉浸式延伸）
     */
    fun setImmersiveStatusBar() {
        val hookLifecycle = object : XC_MethodHook() {
            @Suppress("DEPRECATION")
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                val window = activity.window ?: return
                val decorView = window.decorView

                // 允许内容延伸到状态栏下方
                window.setDecorFitsSystemWindows(false)

                // 设置状态栏背景完全透明，避免黑底或原生系统半透明遮罩
                window.statusBarColor = android.graphics.Color.TRANSPARENT

                // 清除半透明系统栏 Flag，确保完全透明
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

                val controller = window.insetsController
                // 确保状态栏显示（不隐藏，保留图标与时间）
                controller?.show(WindowInsets.Type.statusBars())

                // 兼容低版本及不同定制 ROM 的沉浸 Flag
                decorView.systemUiVisibility = decorView.systemUiVisibility
                    .or(View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
                    .or(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            }
        }
        XposedHelpers.findAndHookMethod(Activity::class.java, "onCreate", Bundle::class.java, hookLifecycle)
        XposedHelpers.findAndHookMethod(Activity::class.java, "onResume", hookLifecycle)
        XposedHelpers.findAndHookMethod(Activity::class.java, "onWindowFocusChanged", Boolean::class.javaPrimitiveType, hookLifecycle)

        // 防止 Edge 内部页面切换或全屏切换时重新设置 statusBarColor
        XposedHelpers.findAndHookMethod(
            android.view.Window::class.java,
            "setStatusBarColor",
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[0] = android.graphics.Color.TRANSPARENT
                }
            }
        )
    }
}
