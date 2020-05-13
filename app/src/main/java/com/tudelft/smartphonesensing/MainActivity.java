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
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.room.Room;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private WifiManager wifiManager;

    private final int MY_PERMISSIONS_ACCESS_FINE_LOCATION = 1;
    //set BSSID here
    private String BSSID="50:46:5d:cc:d7:b0";
    private ArrayList<Scan> aggregatedResults;
    private Handler handler;
    //number of times to check RSSI
    private int iterationsOfWifiScan =0;
    private final int iterationsLimit=20;
    private GraphView graphRSSI;
    private GraphView graphQuality;
    private ProgressBar loadingBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FloatingActionButton fab = findViewById(R.id.fab);
        graphRSSI = (GraphView) findViewById(R.id.graphRSSI);
        graphQuality = (GraphView) findViewById(R.id.graphQuality);
        loadingBar=(ProgressBar)findViewById(R.id.progress_loader);
        fab.setOnClickListener(this);
        aggregatedResults=new ArrayList<>();
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case R.id.fab:
                //check permissions
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_ACCESS_FINE_LOCATION);
                }
                beginWifiScanAndShowGraph();

                break;
            default: break;

        }

    }

    private void beginWifiScanAndShowGraph() {

        iterationsOfWifiScan=0; //reset for next scan
        handler=new Handler();
        //view handling
        graphRSSI.setVisibility(View.INVISIBLE);
        graphQuality.setVisibility(View.INVISIBLE);
        loadingBar.setVisibility(View.VISIBLE);

        Runnable runnableCode = new Runnable() {
           @Override
           public void run() {
               // Do something here on the main thread
               iterationsOfWifiScan++;
               Log.d("Handlers", "Called on main thread");
               startScan();
               handler.postDelayed(this, 1000);
               loadingBar.setProgress(iterationsOfWifiScan*100/iterationsLimit);
               if(iterationsOfWifiScan ==iterationsLimit) {
                   //show the data

                   handler.removeCallbacks(this);
                   ArrayList<DataPoint> dpList=new ArrayList<>();
                   for(int i=0;i<aggregatedResults.size();i++){
                       DataPoint dp=new DataPoint(i,aggregatedResults.get(i).getRSSi());
                       dpList.add(dp);
                   }
                   DataPoint[] dpArray= dpList.toArray(new DataPoint[0]);
                   LineGraphSeries< DataPoint > series = new LineGraphSeries<>(dpArray);
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
                   loadingBar.setVisibility(View.INVISIBLE);
                   //TODO: move this to seperate fragments
               }

           }
       };
        handler.post(runnableCode);
        Toast.makeText(getApplicationContext()," Beginning Scan",Toast.LENGTH_SHORT).show();
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

        // Get the database
        // Get database
        final AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "production")
                .allowMainThreadQueries()
                .build();

        if(results.size()!=0) {
            for (ScanResult scanResult : results) {
                Log.v("Wifi", scanResult.toString());
                String MAC = scanResult.BSSID;
                String SSID = scanResult.SSID;
                int RSSi = scanResult.level;
                int level = wifiManager.calculateSignalLevel(scanResult.level, 10);
                int freq = scanResult.frequency;
                // TODO: Change this to the user choosing which location they are in for training
                String loc = "A";
                long time = scanResult.timestamp;
                Scan result = new Scan(MAC, SSID, RSSi, level, freq, loc, time);
                db.scanDAO().InsertAll(result);
                Log.v("DB", "Added scan: " + SSID + " " + MAC + " " + RSSi + " " +
                        level + " " + freq + " " + loc + " " + time);
                if(scanResult.BSSID.equalsIgnoreCase(BSSID))
                {
                    aggregatedResults.add(result);
                }

            }
        }
        else{
            Toast.makeText(getApplicationContext(),"No AP found", Toast.LENGTH_SHORT).show();

        }
    }

    private void scanFailure() {
        // handle failure: new scan did NOT succeed
        // consider using old scan results: these are the OLD results!
        Toast.makeText(getApplicationContext(),"failed to scan",Toast.LENGTH_SHORT).show();
        List<ScanResult> results = wifiManager.getScanResults();

    }
}
