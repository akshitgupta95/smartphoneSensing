package com.example.example2;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.room.Room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Smart Phone Sensing Example 2. Working with sensors.
 */
public class MainActivity extends Activity implements OnClickListener, AdapterView.OnItemSelectedListener {

    /**
     * The wifi manager.
     */
    private WifiManager wifiManager;
    /**
     * The wifi info.
     */
    private WifiInfo wifiInfo;

    /**
     * Text fields to show the sensor values.
     */

    WifiManager wifi;
    ListView lv;
    TextView textStatus;
    Button buttonScan, buttonCheck;
    int size = 0;
    List<ScanResult> results;


    String ITEM_KEY = "key";
    ArrayList<HashMap<String, String>> arraylist = new ArrayList<HashMap<String, String>>();
    SimpleAdapter adapter;

    private static final String TAG = "AddWifi";

    private String loc = "A";

    /* Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonScan = (Button) findViewById(R.id.buttonScan);
        buttonCheck = (Button) findViewById(R.id.btn_checkdb);
        buttonScan.setOnClickListener((OnClickListener) this);
        buttonCheck.setOnClickListener((OnClickListener) this);
        lv = (ListView)findViewById(R.id.list);

        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (!wifi.isWifiEnabled())
        {
            Toast.makeText(getApplicationContext(), "wifi is disabled..making it enabled", Toast.LENGTH_LONG).show();
            wifi.setWifiEnabled(true);
        }
        this.adapter = new SimpleAdapter(MainActivity.this, arraylist, R.layout.custom_row, new String[] { ITEM_KEY }, new int[] { R.id.list_value });
        lv.setAdapter(this.adapter);

        registerReceiver(new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context c, Intent intent)
            {
                results = wifi.getScanResults();
                size = results.size();
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        Spinner spinner = findViewById(R.id.spinner);
        ArrayAdapter<CharSequence> spin_adapter = ArrayAdapter.createFromResource(this, R.array.locations, android.R.layout.simple_spinner_item);
        spin_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spin_adapter);
        spinner.setOnItemSelectedListener(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void onClick(View view)
    {
        if(view == buttonScan){
            arraylist.clear();
            wifi.startScan();

            // We will have to put this in a Room database

            Toast.makeText(this, "Scanning...." + size, Toast.LENGTH_SHORT).show();
            try
            {
                size = size - 1;
                while (size >= 0)
                {
                    HashMap<String, String> item = new HashMap<String, String>();
                    String ssid = results.get(size).SSID;
                    int rssi = results.get(size).level;
//                maxLevel = Math.max(level, maxLevel);
//                int norm_level = WifiManager.calculateSignalLevel(rssi, maxLevel);
                    ssid = ssid.isEmpty() ? "[UNDEFINED]" : ssid;
                    item.put(ITEM_KEY, ssid + "\t \t" + rssi + " dB");
                    String mac = results.get(size).BSSID;
                    int freq = results.get(size).frequency;
                    long time = results.get(size).timestamp;


                    arraylist.add(item);

                    // Get database
                    final AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "production")
                            .allowMainThreadQueries()
                            .build();

                    Wifi wifi = new Wifi(ssid, mac, rssi, freq, this.loc, time);
                    Log.v(TAG, "added wifi: " + ssid + " " + mac + " " + rssi + " " + freq + " " + this.loc + " " + time);
                    db.wifiDao().InsertAll(wifi);
                    size--;
                    adapter.notifyDataSetChanged();
                }
            }
            catch (Exception e)
            { }
        } else if(view == buttonCheck){
            startActivity(new Intent(MainActivity.this, CheckDB.class));
        }

    }


    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        this.loc = parent.getItemAtPosition(position).toString();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}