package com.tudelft.smartphonesensing;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.widget.Toast;

import androidx.room.Room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bayes {

    Context c;
    public List<MACTable> MacTables = new ArrayList<MACTable>();

    public Bayes(Context context) {
        c = context;
    }

    // MACTable class
    public class MACTable {
        private String location;
        private Map<String, Map<Integer, Integer>> table;// This nested  hashmap has too much data, avoid hash collisions

        public MACTable(String location, Map<String, Map<Integer, Integer>> table) {
            this.location = location;
            this.table = table;
        }
    }

    public void createTable() {
        final AppDatabase db = Room.databaseBuilder(c.getApplicationContext(), AppDatabase.class, "production")
                .allowMainThreadQueries()
                .build();

        // First get all locations
        List<String> locations = db.scanDAO().getAllLocations(); //O(N) //TODO: break the monolith O(1), or make loc primary key: O(logN)
        // Make a table for every location/cell
        for(String location : locations){
            // query all scanResults for this location
            List<Scan> scans = db.scanDAO().getAllScansLoc(location); //O(N)//TODO: important, bad db schema, loc primary key:O(logN), seperated tables on location O(1)
            // Make new table with initialized recordlist
            MACTable macTable = new MACTable(location, new HashMap<String, Map<Integer, Integer>>());

            // for every scanned Wifi beacon on that location, fill the table
            for(Scan scan : scans) {
                // get MAC and RSSi of current scan
                String curMAC = scan.getMAC();
                int curRSSi = scan.getRSSi();

                // if the table contains a MACrecord of the scanned MAC (CONTAINS ROW)
                if (macTable.table.containsKey(curMAC)) {
                    // Get the RSSi frequencies of the MacRecord
                    Map<Integer, Integer> freqsRSSi = macTable.table.get(curMAC);

                    // If the current RSSi has been found before update frequency (CONTAINS COLUMN)
                    if(freqsRSSi.containsKey(curRSSi)){
                        freqsRSSi.put(curRSSi, freqsRSSi.get(curRSSi) + 1);
                        // Else add new RSSi with frequency 1 (NEW COLUMN)
                    } else {
                        freqsRSSi.put(curRSSi, 1);
                    }
                    macTable.table.put(curMAC, freqsRSSi);

                    // Else put a new MAC record in the MAC table. (NEW ROW)
                } else {
                    // Put new entry in with frequency 1 for the RSSi (NEW COLUMN)
                    Map<Integer, Integer> newRSSi = new HashMap<Integer, Integer>();
                    newRSSi.put(curRSSi, 1);
                    macTable.table.put(curMAC, newRSSi);
                }
            }

            MacTables.add(macTable);

        }

    }

    public String predictLocation(List<ScanResult> scanResults){
        final AppDatabase db = Room.databaseBuilder(c.getApplicationContext(), AppDatabase.class, "production")
                .allowMainThreadQueries()
                .build();//TODO: Make singleton// Move to model class

        // First get all locations from the database
        List<String> locations = db.scanDAO().getAllLocations(); //O(N)

        // Make a probability hashmap for P(loc|wifi)
        Map<String, Double> ProbLocWifi = new HashMap<String, Double>();
        for(String location : locations) {
            ProbLocWifi.put(location, 1.0);
        }

        // Set local list of MacTables
        List<MACTable> candidateList = MacTables;


        // For every scan provided
        for(ScanResult scan : scanResults) {
            // get MAC and RSSi
            String curMAC = scan.BSSID;
            int curRSSi = scan.level;

            // Check all the macTables of every location
            for(MACTable macTable : candidateList){  //O(N) TODO: looping over almost the entire DB, not needed See lecture 4 for each prediction
                // If the scanned MAC exists in this location table (GET ROW)
                if(macTable.table.containsKey(curMAC)) {
                    // Get the RSSis for the MAC (GET COLUMNS)
                    Map<Integer, Integer> freqsRSSi = macTable.table.get(curMAC);

                    // Check if the RSSi value exists (GET COLUMN)
                    if(freqsRSSi.containsKey(curRSSi)) {
                        // Get probability P(RSSi_i|MAC, location)
                        int sum = 0; //TODO: precompute while storing or making table, store prob values instead of RSSi freq
                        for(int freq : freqsRSSi.values()){
                            sum+=freq;
                        }
                        Double prob = freqsRSSi.get(curRSSi).doubleValue() / sum;

                        // Set P(location|MAC)
                        ProbLocWifi.put(macTable.location, ProbLocWifi.get(macTable.location) * prob);
                    } else {
                        // Table gets removed from candidates and probability set to 0
                        ProbLocWifi.put(macTable.location, ProbLocWifi.get(macTable.location) * 0.9);
//                        candidateList.remove(macTable);
                    }
                } else {
                    // Table gets removed from candidates
                    ProbLocWifi.put(macTable.location, ProbLocWifi.get(macTable.location) * 0.9);
//                    candidateList.remove(macTable);
                }
            }
        }

        // Get the location with highest probability by going to the list of P(MAC| location)
        String curLocation = "None";
        Double maxProb = 0.0;

        for (Map.Entry<String, Double> entry : ProbLocWifi.entrySet()) {
            if(entry.getValue() > maxProb){
                curLocation = entry.getKey();
                maxProb = entry.getValue();
            }
        }

        Toast.makeText(c.getApplicationContext(),"Probability :" + maxProb,Toast.LENGTH_SHORT).show();
        // Return the location
        return curLocation;
    }

}
