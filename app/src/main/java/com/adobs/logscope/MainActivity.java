package com.adobs.logscope;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.adobs.logscope.adapters.AppListAdapter;
import com.adobs.logscope.core.VirtualCore;
import com.adobs.logscope.models.AppInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private RecyclerView recyclerView;
    private AppListAdapter adapter;
    private ProgressBar progressBar;
    
    // ExecutorService को ग्लोबल बनाया गया है ताकि इसे नष्ट (Destroy) किया जा सके
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Thread Pool Initialize करना
        executorService = Executors.newSingleThreadExecutor();

        checkStoragePermission();
        loadInstalledApps();
    }

    /**
     * सुरक्षित तरीके से बैकग्राउंड में ऐप्स लोड करना
     */
    private void loadInstalledApps() {
        progressBar.setVisibility(View.VISIBLE);
        Handler handler = new Handler(Looper.getMainLooper());

        executorService.execute(() -> {
            List<AppInfo> tempApps = new ArrayList<>();
            PackageManager pm = getPackageManager();
            
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
                            // अगर किसी एक ऐप का डेटा करप्ट है, तो पूरा लूप क्रैश नहीं होगा
                            Log.w(TAG, "Failed to load icon/label for package: " + app.packageName);
                        }
                    }
                }
                Collections.sort(tempApps, (o1, o2) -> o1.appName.compareToIgnoreCase(o2.appName));
            } catch (Exception e) {
                Log.e(TAG, "Error fetching installed applications", e);
            }

            // Main Thread में सुरक्षित वापसी
            handler.post(() -> {
                // SAFETY CHECK: अगर ऐप बंद हो चुका है, तो UI अपडेट मत करो
                if (isDestroyed() || isFinishing()) return;

                progressBar.setVisibility(View.GONE);
                adapter = new AppListAdapter(tempApps, app -> launchInVirtualEngine(app));
                recyclerView.setAdapter(adapter);
            });
        });
    }

    /**
     * ऐप को BlackBox इंजन में इंस्टॉल और लॉन्च करना
     */
    private void launchInVirtualEngine(AppInfo app) {
        Toast.makeText(this, "Preparing " + app.appName + " in LogScope...", Toast.LENGTH_SHORT).show();
        
        // नए Thread के बजाय उसी ExecutorService का इस्तेमाल करें ताकि थ्रेड्स की संख्या न बढ़े
        executorService.execute(() -> {
            try {
                VirtualCore.get(MainActivity.this).installAndLaunch(app.packageName);
            } catch (Exception e) {
                Log.e(TAG, "Virtual Engine Launch Failed for: " + app.packageName, e);
            }
        });
    }

    /**
     * Storage Permission Logic (Android 11+ Compatible)
     */
    private void checkStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    Toast.makeText(this, "Please allow 'All Files Access' to save Target Logs", Toast.LENGTH_LONG).show();
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Permission check failed", e);
        }
    }

    /**
     * MEMORY LEAK PREVENTION (बहुत जरूरी)
     * जब Activity नष्ट होती है, तो सभी बैकग्राउंड काम रोक दें।
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow(); // चालू काम को तुरंत रोकें
        }
    }
}
