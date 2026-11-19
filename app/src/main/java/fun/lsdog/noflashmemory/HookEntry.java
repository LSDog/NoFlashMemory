package fun.lsdog.noflashmemory;

import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 禁用 ColorOS 三指上滑「一键闪记」的 LSPosed 模块入口。
 *
 * <p>原理：三指上滑的检测与触发位于 system_server（framework）进程中的
 * {@code com.android.server.oplus.input.exinputservice.globalgesture.OplusMultiGestureThreeStrategy}
 * （来自 /system/framework/oplus-services.jar）。上滑触发链为：</p>
 *
 * <pre>
 *   notifyThreeSwipeGesture
 *     -> onGestureMessage(MSG_THREE_SWIPE_UP)
 *       -> handleThreeSwipeUp(int, int, Bundle)
 *         -> startColorDirectservice(Bundle, boolean)
 *           -> 启动闪记服务 -> 弹出「是否启用一键闪记」对话框
 * </pre>
 *
 * <p>本模块在 trigger 入口直接终止原方法，从而彻底禁用三指上滑的检测结果落地。
 * 三指下滑截图、左右滑、三指长按等手势走各自独立的方法，不受影响。</p>
 */
public final class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "NoFlashMemory";

    /** framework / system_server 在 Xposed 中包名固定为 "android" */
    private static final String FRAMEWORK_PACKAGE = "android";

    /** 三指手势策略类（system_server 内） */
    private static final String CLASS_THREE_GESTURE =
            "com.android.server.oplus.input.exinputservice.globalgesture.OplusMultiGestureThreeStrategy";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!FRAMEWORK_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        XposedBridge.log(TAG + ": framework loaded, installing hooks");

        // 主拦截：三指上滑的专属触发入口
        try {
            XposedHelpers.findAndHookMethod(
                    CLASS_THREE_GESTURE,
                    lpparam.classLoader,
                    "handleThreeSwipeUp",
                    int.class, int.class, Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                            XposedBridge.log(TAG + ": blocked handleThreeSwipeUp");
                        }
                    });
            XposedBridge.log(TAG + ": hook handleThreeSwipeUp installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook handleThreeSwipeUp FAILED: " + t);
        }

        // 双保险：闪记服务启动点（仅被 handleThreeSwipeUp 调用），
        // 防止系统 OTA 后调用链发生变化时仍能弹出对话框
        try {
            XposedHelpers.findAndHookMethod(
                    CLASS_THREE_GESTURE,
                    lpparam.classLoader,
                    "startColorDirectservice",
                    Bundle.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                            XposedBridge.log(TAG + ": blocked startColorDirectservice");
                        }
                    });
            XposedBridge.log(TAG + ": hook startColorDirectservice installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook startColorDirectservice FAILED: " + t);
        }
    }
}
