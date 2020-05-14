package com.tudelft.smartphonesensing;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.room.Room;

import java.util.List;

public class PredictActivity extends Activity {

    private WifiManager wifiManager;
    private List<ScanResult> results;
    private Bayes bayes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.predict_activity);
        // Build the prediction table
        bayes = new Bayes(this);
        bayes.createTable();
        beginWifiScanAndLocate();
    }

    private void beginWifiScanAndLocate() {
        Toast.makeText(getApplicationContext(),"Scanning Wifi...",Toast.LENGTH_SHORT).show();
        startScan();
        Toast.makeText(getApplicationContext(),"Predicting location...",Toast.LENGTH_SHORT).show();
    }

    private void startScan() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                boolean success = intent.getBooleanExtra(
                        WifiManager.EXTRA_RESULTS_UPDATED, false);
                if (success) {
                    scanSuccess();
                } else {
                    // scan failure handling
                    scanFailure();
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        getApplicationContext().registerReceiver(wifiScanReceiver, intentFilter);

        boolean success = wifiManager.startScan();
        if (!success) {
            // scan failure handling
            scanFailure();
        }
    }
    private void scanSuccess() {
        List<ScanResult> results = wifiManager.getScanResults();

        if(results.size()!=0) {
            String location = bayes.predictLocation(results);
            TextView locationText = (TextView)findViewById(R.id.text_loc);
            locationText.setText(location);
        }
    }

    private void scanFailure() {
        // handle failure: new scan did NOT succeed
        // consider using old scan results: these are the OLD results!
        Toast.makeText(getApplicationContext(),"failed to scan",Toast.LENGTH_SHORT).show();
        this.results = wifiManager.getScanResults();
    }
}
