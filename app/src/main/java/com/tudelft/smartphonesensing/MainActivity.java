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
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private WifiManager wifiManager;

    private final int MY_PERMISSIONS_ACCESS_FINE_LOCATION = 1;
    private String BSSID="18:D6:C7:79:14:EA";
    private ArrayList<ScanResultDAO> aggregatedResults;
    private Handler handler;
    private int i=0;
    private GraphView graphRSSI;
    private GraphView graphQuality;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FloatingActionButton fab = findViewById(R.id.fab);
        graphRSSI = (GraphView) findViewById(R.id.graphRSSI);
        graphQuality = (GraphView) findViewById(R.id.graphQuality);
        fab.setOnClickListener(this);
        aggregatedResults=new ArrayList<>();

    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case R.id.fab:
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_ACCESS_FINE_LOCATION);
                }
                i=0;
                handler=new Handler();
                graphRSSI.setVisibility(View.INVISIBLE);
                graphQuality.setVisibility(View.INVISIBLE);

                 Runnable runnableCode = new Runnable() {
                    @Override
                    public void run() {
                        // Do something here on the main thread
                        i++;
                        Log.d("Handlers", "Called on main thread");
                        startScan();
                        handler.postDelayed(this, 1000);
                        if(i==20) {
                            handler.removeCallbacks(this);
                            ArrayList<DataPoint> dpList=new ArrayList<>();
                            for(int i=0;i<aggregatedResults.size();i++){
                                DataPoint dp=new DataPoint(i,aggregatedResults.get(i).getRSSI());
                                dpList.add(dp);
                            }
                            DataPoint[] dpArray= dpList.toArray(new DataPoint[0]);
                            LineGraphSeries < DataPoint > series = new LineGraphSeries<>(dpArray);
                            graphRSSI.setVisibility(View.VISIBLE);
                            graphRSSI.addSeries(series);
                            graphRSSI.setTitle("RSSI");

                            ArrayList<DataPoint> dpQualityList=new ArrayList<>();
                            for(int i=0;i<aggregatedResults.size();i++){
                                DataPoint dp=new DataPoint(i,aggregatedResults.get(i).getLevel());
                                dpQualityList.add(dp);
                            }
                            DataPoint[] dpQualityArray= dpQualityList.toArray(new DataPoint[0]);
                            LineGraphSeries < DataPoint > series2 = new LineGraphSeries<>(dpQualityArray);
                            graphQuality.setVisibility(View.VISIBLE);
                            graphQuality.addSeries(series2);
                            graphQuality.setTitle("Quality");
                        }
                        // Repeat this the same runnable code block again another 2 seconds
                        // 'this' is referencing the Runnable object

                    }
                };
                 handler.post(runnableCode);
                Toast.makeText(getApplicationContext()," Beginning Scan",Toast.LENGTH_SHORT).show();

                break;
            default: break;

        }

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
            for (ScanResult scanResult : results) {
                if(scanResult.BSSID.equalsIgnoreCase(BSSID))
                {
                    ScanResultDAO result=new ScanResultDAO((scanResult.level),wifiManager.calculateSignalLevel(scanResult.level, 10));
                    aggregatedResults.add(result);
                }

            }
        }
        else{

        }
    }

    private void scanFailure() {
        // handle failure: new scan did NOT succeed
        // consider using old scan results: these are the OLD results!
        Toast.makeText(getApplicationContext(),"failed to scan",Toast.LENGTH_SHORT).show();
        List<ScanResult> results = wifiManager.getScanResults();

    }
}
