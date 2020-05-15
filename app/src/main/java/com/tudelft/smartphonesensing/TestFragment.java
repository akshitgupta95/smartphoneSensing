package com.tudelft.smartphonesensing;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class TestFragment extends Fragment implements View.OnClickListener {

    private WifiManager wifiManager;
    private List<ScanResult> results;
    private Bayes bayes;
    private boolean buildTable = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.test_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FloatingActionButton pred = (FloatingActionButton) getView().findViewById(R.id.pred);
        pred.setOnClickListener(this);
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.pred:
                bayes = new Bayes(getActivity());
                // TODO: only create table when database is updated so prediction is faster
                if (buildTable) {
                    bayes.createTable();
                    buildTable = false;
                }
                beginWifiScanAndLocate();

                break;
            default:
                break;
        }
    }

    // Remove this it rebuilds the table after the app is opened again.
    @Override
    public void onResume() {
        super.onResume();
        buildTable = true;
    }

    private void beginWifiScanAndLocate() {
        Toast.makeText(getActivity().getApplicationContext(), "Scanning Wifi...", Toast.LENGTH_SHORT).show();
        startScan();
        Toast.makeText(getActivity().getApplicationContext(), "Predicting location...", Toast.LENGTH_SHORT).show();
    }

    private void startScan() {
        WifiScanner scanner = new WifiScanner(getContext(), 1, this::scanSuccess, null, this::scanFailure);
    }

    private void scanSuccess(List<ScanResult> results) {
        if (results.size() != 0) {
            String location = bayes.predictLocation(results);
            TextView locationText = (TextView) getView().findViewById(R.id.text_loc);
            locationText.setText(location);
        }
    }

    private void scanFailure() {
        // handle failure: new scan did NOT succeed
        // consider using old scan results: these are the OLD results!
        Toast.makeText(getActivity().getApplicationContext(), "failed to scan", Toast.LENGTH_SHORT).show();
        this.results = wifiManager.getScanResults();
    }
}
