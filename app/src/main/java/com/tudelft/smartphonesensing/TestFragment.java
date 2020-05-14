package com.tudelft.smartphonesensing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

public class TestFragment extends Fragment {

    private WifiManager wifiManager;
    private List<ScanResult> results;
    private Bayes bayes;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        return inflater.inflate(R.layout.test_fragment, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bayes = new Bayes(getActivity());
        bayes.createTable();
        beginWifiScanAndLocate();
    }

    private void beginWifiScanAndLocate() {
        Toast.makeText(getActivity().getApplicationContext(),"Scanning Wifi...",Toast.LENGTH_SHORT).show();
        startScan();
        Toast.makeText(getActivity().getApplicationContext(),"Predicting location...",Toast.LENGTH_SHORT).show();
    }

    private void startScan() {
        wifiManager = (WifiManager) getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
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
        getActivity().getApplicationContext().registerReceiver(wifiScanReceiver, intentFilter);

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
            TextView locationText = (TextView) getView().findViewById(R.id.text_loc);
            locationText.setText(location);
        }
    }

    private void scanFailure() {
        // handle failure: new scan did NOT succeed
        // consider using old scan results: these are the OLD results!
        Toast.makeText(getActivity().getApplicationContext(),"failed to scan",Toast.LENGTH_SHORT).show();
        this.results = wifiManager.getScanResults();
    }
}
