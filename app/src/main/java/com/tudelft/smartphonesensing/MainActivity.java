package com.tudelft.smartphonesensing;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    ArrayAdapter<String> adapter;
    WifiManager wifiManager;
    List<ScanResult> lastWifiResult = new ArrayList<>();
    List<String> lastWifiStrings = new ArrayList<>();
    Button refreshButton;
    Button uploadButton;
    ListView wifiList;
    TextView statustext;
    final String location = android.Manifest.permission.ACCESS_FINE_LOCATION;
    RequestQueue queue;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //init views
        setContentView(R.layout.activity_main);
        wifiList = findViewById(R.id.wifilist);
        refreshButton = findViewById(R.id.refresh);
        uploadButton = findViewById(R.id.uploadbutton);
        statustext = findViewById(R.id.status);

        uploadButton.setOnClickListener(e -> submit());
        refreshButton.setOnClickListener(e -> scan());

        //init resources
        queue = Volley.newRequestQueue(this);
        wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        boolean hasWifiRtt = false;
        boolean hasWifiRttEnabled = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            hasWifiRtt = this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT);
            hasWifiRttEnabled = wifiManager.isDeviceToApRttSupported();
        }
        statustext.setText(String.format("RTT hardware: %s, enabled: %s", hasWifiRtt ? "yes" : "no", hasWifiRttEnabled ? "yes" : "no"));

        //need either this statement or the next one
        if (ActivityCompat.checkSelfPermission(this, location) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);


        //call this to open settings menu on the toggle for location services
        //this needs to be enabled, but currently not sure how to detect
        //startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));


        //listen for wifi changes
        BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                onScan(success);
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        this.registerReceiver(wifiScanReceiver, intentFilter);

        adapter = new ArrayAdapter<>(this, R.layout.wifirow, lastWifiStrings);
        wifiList.setAdapter(adapter);
        scan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scan();
    }

    void scan() {
        boolean success = wifiManager.startScan();
        if (!success) {
            onScan(false);
        }
    }

    //currently obsolete
    void submit() {
        String reqstr;
        try {
            JSONArray data = new JSONArray();
            for (ScanResult sc : lastWifiResult) {
                JSONObject scjson = new JSONObject();
                scjson.put("rssi", sc.level);
                scjson.put("frequency", sc.frequency);
                scjson.put("time", sc.timestamp);
                scjson.put("hasrtt", sc.is80211mcResponder());
                scjson.put("name", sc.SSID);
                scjson.put("mac", sc.BSSID);
                data.put(sc);
            }
            reqstr = data.toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        new StringRequest(Request.Method.POST, "http://localhost:8080/submitmeasure", str -> {
            try {
                JSONObject obj = new JSONObject(str);
            } catch (JSONException e) {
                submitError("bad server response");
            }
        }, err -> {
            submitError("http request failed");
        });
    }

    void submitError(String errortxt) {
        //TODO
    }

    private void onScan(boolean success) {
        lastWifiResult.clear();
        lastWifiResult.addAll(wifiManager.getScanResults());
        lastWifiStrings.clear();
        lastWifiStrings.addAll(lastWifiResult.stream().map(q -> {
            return String.format("dBm: %s [%s] %s RTT: %s", q.level, q.BSSID, (q.SSID.isEmpty() ? "[no name]" : q.SSID), (q.is80211mcResponder() ? "yes" : "no"));
        }).collect(Collectors.toList()));
        adapter.notifyDataSetInvalidated();
    }
}
