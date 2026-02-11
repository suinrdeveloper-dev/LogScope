package com.adobs.logscope.core;

import android.util.Log;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.MethodHook;

public class LogHook {

    private static final String TAG = "LogScope_Hook";

    /**
     * Target App के Log methods को सुरक्षित रूप से Intercept करता है।
     */
    public static void startHooking() {
        try {
            // Target Class
            Class<?> logClass = Class.forName("android.util.Log");

            // सभी संभव Log लेवल्स
            String[] methods = {"v", "d", "i", "w", "e", "wtf"};

            for (String methodName : methods) {
                BlackBoxCore.get().getHookManager().addMethodHook(logClass, methodName, new MethodHook() {
                    
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        
                        // SAFETY 1: हुक के अंदर कोई भी एरर Target App को क्रैश नहीं करना चाहिए
                        try {
                            if (param.args == null || param.args.length < 2) {
                                return;
                            }

                            // SAFETY 2: Type Checking (क्या वे वास्तव में String हैं?)
                            if (!(param.args[0] instanceof String) || !(param.args[1] instanceof String)) {
                                return;
                            }

                            String tag = (String) param.args[0];
                            String msg = (String) param.args[1];
                            String level = methodName.toUpperCase();

                            // SAFETY 3: Exception Handling (अगर Log.e में Throwable भेजा गया है)
                            StringBuilder exceptionData = new StringBuilder();
                            if (param.args.length >= 3 && param.args[2] instanceof Throwable) {
                                Throwable tr = (Throwable) param.args[2];
                                exceptionData.append("\n").append(Log.getStackTraceString(tr));
                            }

                            // Log Format तैयार करना
                            String fullLog = String.format("[%s/%s] %s%s", level, tag, msg, exceptionData.toString());

                            // LogManager को भेजना 
                            // (आदर्श रूप में यह एसिंक्रोनस (Asynchronous) होना चाहिए ताकि Target App धीमा न हो)
                            LogManager.write(fullLog);

                        } catch (Exception ex) {
                            // Target App को क्रैश होने से बचाने के लिए Exception को यहीं म्यूट करें
                            Log.e(TAG, "Log interception failed silently", ex);
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize LogHook", e);
        }
    }
}
