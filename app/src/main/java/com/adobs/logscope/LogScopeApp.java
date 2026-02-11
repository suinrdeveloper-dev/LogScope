package com.adobs.logscope;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.adobs.logscope.core.LogHook;
import com.adobs.logscope.core.LogManager;

import top.niunaijun.blackbox.BlackBoxCore;

public class LogScopeApp extends Application {

    private static final String TAG = "LogScopeApp";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            BlackBoxCore.get().doAttachBaseContext(base);
        } catch (Exception e) {
            Log.e(TAG, "Fatal Error: BlackBoxCore failed to attach base context", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            BlackBoxCore.get().doOnCreate();

            if (BlackBoxCore.get().isVirtualProcess()) {
                String processName = resolveProcessName();
                
                LogManager.init(processName);
                LogHook.startHooking();
                
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Fatal Error: BlackBoxCore initialization failed in onCreate", e);
        }
    }

    /**
     * Safely resolves the target application's process/package name.
     * Prevents NullPointerExceptions if the virtualization engine drops the context.
     */
    private String resolveProcessName() {
        try {
            // Attempt standard BlackBox API fetch
            String processName = top.niunaijun.blackbox.app.BActivityThread.currentProcessName();
            if (processName != null && !processName.isEmpty()) {
                return processName;
            }
        } catch (Exception | Error e) {
            Log.w(TAG, "BActivityThread.currentProcessName() failed. Falling back.", e);
        }

        // Fallback safety string if reflection or internal API fails
        return "Unknown_Virtual_Process";
    }
}
