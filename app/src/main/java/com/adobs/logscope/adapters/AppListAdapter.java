package com.adobs.logscope.viewmodels;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.adobs.logscope.models.AppInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppListViewModel extends AndroidViewModel {

    private static final String TAG = "AppListViewModel";
    
    // LiveData UI को अपने आप अपडेट कर देता है जब डेटा तैयार हो जाता है
    private final MutableLiveData<List<AppInfo>> appListLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoadingLiveData = new MutableLiveData<>();
    
    private final ExecutorService executorService;

    public AppListViewModel(@NonNull Application application) {
        super(application);
        executorService = Executors.newSingleThreadExecutor();
        loadInstalledApps(); // ViewModel बनते ही ऐप्स लोड करना शुरू कर देगा
    }

    public LiveData<List<AppInfo>> getAppList() {
        return appListLiveData;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoadingLiveData;
    }

    private void loadInstalledApps() {
        isLoadingLiveData.setValue(true);
        
        executorService.execute(() -> {
            List<AppInfo> tempApps = new ArrayList<>();
            PackageManager pm = getApplication().getPackageManager();
            
            try {
                List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

                for (ApplicationInfo app : packages) {
                    if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        try {
                            String name = pm.getApplicationLabel(app).toString();
                            String pkg = app.packageName;
                            Drawable icon = pm.getApplicationIcon(app);
                            
                            tempApps.add(new AppInfo(name, pkg, icon));
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to load icon/label for package: " + app.packageName);
                        }
                    }
                }
                // FIX: getAppName() का इस्तेमाल क्योंकि fields अब सुरक्षित (private) हैं
                Collections.sort(tempApps, (o1, o2) -> o1.getAppName().compareToIgnoreCase(o2.getAppName()));
            } catch (Exception e) {
                Log.e(TAG, "Error fetching installed applications", e);
            }

            // postValue अपने आप Main Thread पर जाकर डेटा भेज देता है
            appListLiveData.postValue(tempApps);
            isLoadingLiveData.postValue(false);
        });
    }

    public void executeBackgroundLaunch(Runnable task) {
        executorService.execute(task);
    }

    /**
     * जब ऐप पूरी तरह से बंद हो जाएगा, तभी यह Executor को शटडाउन करेगा।
     * स्क्रीन रोटेट होने पर यह शटडाउन नहीं होगा (No data loss).
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}
