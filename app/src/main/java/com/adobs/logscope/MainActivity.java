package com.adobs.logscope;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.adobs.logscope.adapters.AppListAdapter;
import com.adobs.logscope.core.VirtualCore;
import com.adobs.logscope.models.AppInfo;
import com.adobs.logscope.viewmodels.AppListViewModel;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private AppListViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // ViewModel को इनिशियलाइज़ करना (यह रोटेशन पर सुरक्षित रहता है)
        viewModel = new ViewModelProvider(this).get(AppListViewModel.class);

        checkStoragePermission();
        observeViewModel();
    }

    /**
     * ViewModel से डेटा आने का इंतज़ार करना और UI अपडेट करना
     */
    private void observeViewModel() {
        // Loader (ProgressBar) की स्थिति को ऑब्ज़र्व करना
        viewModel.getIsLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        // App List के डेटा को ऑब्ज़र्व करना
        viewModel.getAppList().observe(this, appInfos -> {
            if (appInfos != null) {
                AppListAdapter adapter = new AppListAdapter(appInfos, this::launchInVirtualEngine);
                recyclerView.setAdapter(adapter);
            }
        });
    }

    /**
     * ऐप को BlackBox इंजन में इंस्टॉल और लॉन्च करना
     */
    private void launchInVirtualEngine(AppInfo app) {
        Toast.makeText(this, "Preparing " + app.getAppName() + " in LogScope...", Toast.LENGTH_SHORT).show();
        
        // काम को ViewModel के सुरक्षित थ्रेड में भेजना
        viewModel.executeBackgroundLaunch(() -> {
            try {
                VirtualCore.get(getApplicationContext()).installAndLaunch(app.getPackageName());
            } catch (Exception e) {
                Log.e(TAG, "Virtual Engine Launch Failed for: " + app.getPackageName(), e);
            }
        });
    }

    /**
     * Storage Permission Logic
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
}
