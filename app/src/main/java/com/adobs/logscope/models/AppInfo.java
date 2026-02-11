package com.adobs.logscope.models;

import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;

/**
 * Immutable Data Model for holding Application Information.
 */
public class AppInfo {
    
    // 'private final' यह सुनिश्चित करता है कि डेटा एक बार सेट होने के बाद बदला न जा सके (Thread-Safe)
    private final String appName;
    private final String packageName;
    private final Drawable icon;

    public AppInfo(@NonNull String appName, @NonNull String packageName, @NonNull Drawable icon) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
    }

    // डेटा को सुरक्षित रूप से पढ़ने के लिए Getter Methods
    @NonNull
    public String getAppName() {
        return appName;
    }

    @NonNull
    public String getPackageName() {
        return packageName;
    }

    @NonNull
    public Drawable getIcon() {
        return icon;
    }
}
