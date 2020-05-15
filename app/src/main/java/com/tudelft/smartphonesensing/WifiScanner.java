package com.tudelft.smartphonesensing;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class WifiScanner {

    //arguments
    private Context context;
    private int targetNumScans;
    private Consumer<List<ScanResult>> scanCallback;
    //TODO add some info about what went wrong
    private Runnable errorCallback;
    private Consumer<List<List<ScanResult>>> finishedCallback;

    private int numScans = 0;
    private List<List<ScanResult>> allResults = new ArrayList<>();
    private WifiManager wifiManager;
    private boolean stopped = false;
    private Handler scanhandler = new Handler();

    public static final int MY_PERMISSIONS_ACCESS_FINE_LOCATION = 1;

    public int getProgress() {
        return 100 * numScans / targetNumScans;
    }

    public WifiScanner(Context context, int numscans, Consumer<List<ScanResult>> onScan, Consumer<List<List<ScanResult>>> onFinished, Runnable onError) {
        this.context = context;
        this.targetNumScans = numscans;
        this.scanCallback = onScan;
        this.errorCallback = onError;
        this.finishedCallback = onFinished;

        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(wifiScanReceiver, intentFilter);

        scanhandler.post(this::triggerScan);
    }

    static boolean ensurePermission(Activity activity) {
        if (ActivityCompat.checkSelfPermission(activity.getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_ACCESS_FINE_LOCATION);
            return false;
        }
        return true;
    }

    private void triggerScan() {
        if (stopped) {
            return;
        }
        numScans++;
        boolean success = wifiManager.startScan();
        if (!success && this.errorCallback != null) {
            //TODO add some sort of error code/information
            errorCallback.run();
            scanhandler.postDelayed(this::triggerScan, 1000);
        }
    }

    private BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                List<ScanResult> results = wifiManager.getScanResults();
                allResults.add(results);
                if (scanCallback != null) {
                    scanCallback.accept(results);
                }
            } else if (errorCallback != null) {
                errorCallback.run();
            }

            if (numScans >= targetNumScans) {
                stop();
            } else {
                scanhandler.postDelayed(() -> triggerScan(), 1000);
            }
        }
    };

    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        if (this.finishedCallback != null) {
            this.finishedCallback.accept(allResults);
        }
        context.unregisterReceiver(wifiScanReceiver);
    }
}



