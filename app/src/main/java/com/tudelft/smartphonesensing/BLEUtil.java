package com.tudelft.smartphonesensing;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// TODO: Need to ask permissions at runtime or change destination API level
// TODO: Remove cached messages.
// TODO when to stop discovering.
public class BLEUtil {

    Context context;

    public BLEUtil(Context context) {
        this.context = context;
    }

    private Handler mHandler = new Handler();

    private List<ScanFilter> filters = new ArrayList<ScanFilter>();

    private BluetoothLeScanner mBluetoothLEScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();

    private String loc;

    private boolean notificationFired = false;

    // TODO: Remove this as we use DP3T
    private ScanCallback mScanCallBack = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if(result != null) {
                String scannedLocation = new String(result.getScanRecord().getServiceData(result.getScanRecord().getServiceUuids().get(0)), Charset.forName("UTF-8"));
                if(scannedLocation.equals(loc)){
                    if(!notificationFired){
                        notificationFired = true;
                        Toast.makeText(context, result.getDevice().getAddress() +  " detected in cell " + scannedLocation, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    notificationFired = false;
                }
                mBluetoothLEScanner.flushPendingScanResults(mScanCallBack);
            }

        }

        @Override
        public void onBatchScanResults(List<ScanResult> results){
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode){
            Log.e("BLE", "Discovery onScanFailed: " + errorCode);
            super.onScanFailed(errorCode);
        }
    };

    private AdvertiseCallback mAdvertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect){
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e("BLE", "Advertising OnStartFailure: " + errorCode);
            super.onStartFailure(errorCode);
        }
    };

    public void discover() {
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(UUID.fromString( context.getResources().getString( R.string.ble_uuid )))).build();
        filters.add(filter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        mBluetoothLEScanner.startScan(filters, settings, mScanCallBack);
    }

    public void advertise(String loc) {
        this.loc = loc;
        BluetoothLeAdvertiser advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();

        advertiser.stopAdvertising(mAdvertisingCallback);

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build();

        ParcelUuid pUuid = new ParcelUuid(UUID.fromString( context.getResources().getString( R.string.ble_uuid )));

        // It advertises which cell it is in.
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(pUuid)
                .addServiceData(pUuid, loc.getBytes())
                .build();

        AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect){
                super.onStartSuccess(settingsInEffect);
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e("BLE", "Advertising OnStartFailure: " + errorCode);
                super.onStartFailure(errorCode);
            }
        };
        advertiser.startAdvertising(settings, data, mAdvertisingCallback);
    }
}
