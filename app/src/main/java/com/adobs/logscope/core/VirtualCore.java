package com.adobs.logscope.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;

public class VirtualCore {

    private static final String TAG = "VirtualCore";
    
    // SAFETY 1: volatile कीवर्ड और Double-Checked Locking (Thread Safety के लिए)
    private static volatile VirtualCore instance;
    
    // SAFETY 2: Application Context का उपयोग (Memory Leak रोकने के लिए)
    private final Context appContext;
    
    // SAFETY 3: Main Thread Handler (Background Thread से सुरक्षित Toast दिखाने के लिए)
    private final Handler mainHandler;

    private VirtualCore(Context context) {
        // Activity Context को Application Context में बदलें
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static VirtualCore get(Context context) {
        if (instance == null) {
            synchronized (VirtualCore.class) {
                if (instance == null) {
                    instance = new VirtualCore(context);
                }
            }
        }
        return instance;
    }

    /**
     * असली ऐप की APK को BlackBox Engine (Virtual Environment) में इंस्टॉल करना।
     * Note: यह मेथड Background Thread से कॉल होना चाहिए।
     */
    public void installAndLaunch(String packageName) {
        try {
            // 1. असली APK का रास्ता (Path) और Validation
            PackageManager pm = appContext.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            
            if (appInfo.sourceDir == null) {
                showToast("Error: APK path not found for " + packageName);
                return;
            }

            File apkFile = new File(appInfo.sourceDir);
            if (!apkFile.exists() || !apkFile.canRead()) {
                showToast("Error: Cannot read APK file at " + appInfo.sourceDir);
                return;
            }

            // 2. चेक करें कि क्या यह पहले से Virtual Box में इंस्टॉल है?
            if (BlackBoxCore.get().isInstalled(packageName)) {
                Log.i(TAG, "App already installed in VirtualBox. Launching directly.");
                launchApp(packageName);
                return;
            }

            // 3. इंस्टॉल प्रक्रिया शुरू करें
            showToast("Installing " + packageName + " inside LogScope...");
            Log.d(TAG, "Starting Virtual Install for: " + apkFile.getAbsolutePath());

            // BlackBox API कॉल (यह Heavy I/O ऑपरेशन है)
            InstallResult result = BlackBoxCore.get().installPackage(apkFile);
            
            if (result.success) {
                showToast("Install Success! Launching...");
                launchApp(packageName);
            } else {
                String errorMsg = "Install Failed: " + result.msg;
                Log.e(TAG, errorMsg);
                showToast(errorMsg);
            }

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Target App not found on device: " + packageName, e);
            showToast("Error: Target app is not installed on this device.");
        } catch (Exception e) {
            Log.e(TAG, "Critical Virtualization Error", e);
            showToast("System Error: " + e.getMessage());
        }
    }

    /**
     * ऐप को वर्चुअल इंजन के अंदर लॉन्च करना
     */
    private void launchApp(String packageName) {
        try {
            boolean launched = BlackBoxCore.get().launchApk(packageName);
            if (!launched) {
                Log.e(TAG, "BlackBox engine failed to launch APK: " + packageName);
                showToast("Failed to launch app. Engine error.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while launching APK", e);
            showToast("Launch Exception: " + e.getMessage());
        }
    }

    /**
     * Helper Method: किसी भी Thread से सुरक्षित रूप से Toast दिखाने के लिए
     */
    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show());
    }
}
