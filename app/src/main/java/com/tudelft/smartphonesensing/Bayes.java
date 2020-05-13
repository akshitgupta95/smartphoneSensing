package com.tudelft.smartphonesensing;

import androidx.room.Room;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Bayes {
    public Bayes() {
    }

    public void createTable() {
        final AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "production")
                .allowMainThreadQueries()
                .build();

        // First get all locations
        List<String> locations = db.scanDAO().getAllLocations();

        // TODO: Create a table for A location.
        for(String location : locations){
            // query all scanResults for this location
            List<Scan> locationScans = db.scanDAO().getAllScansLoc(location);

            // Create a table for this location
            Map<String, Map<Integer, Integer>> MACs = new HashMap<String, Map<Integer, Integer>>();

            // for every scan on that location fill the table
            for(Scan locationscan : locationScans) {
                String curMAC = locationscan.getMAC();
                // if the table contains the MAC of the scan (CONTAINS ROW)
                if (MACs.containsKey(curMAC)) {
                    // Get the RSSi frequencies
                    Map<Integer, Integer> freqsRSSi = MACs.get(curMAC);
                    // Get current RSSi
                    int curRSSi = locationscan.getRSSi();
                    // If the current RSSi has been found before update frequency (CONTAINS COLUMN)
                    if(freqsRSSi.containsKey(curRSSi)){
                        freqsRSSi.put(curRSSi, freqsRSSi.get(curRSSi) + 1);
                    // Else add new RSSi with frequency 1 (NEW COLUMN)
                    } else {
                        freqsRSSi.put(curRSSi, 1);
                    }
                    MACs.put(curMAC, freqsRSSi);

                // Else put a new MAC in the mac table. (NEW ROW)
                } else {
                    // Put new entry in with frequency 1 for the RSSi (NEW COLUMN)
                    Map<Integer, Integer> newRSSi = new HashMap<Integer, Integer>();
                    newRSSi.put(locationscan.getRSSi(), 1);
                    MACs.put(curMAC, newRSSi);
                }
            }
        }

        List<Scan> users = db.scanDAO().getAllScanResults();


    }

    public String predictLocation(List<Scan> scans){
        final AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "production")
                .allowMainThreadQueries()
                .build();

        // First get all locations
        List<String> locations = db.scanDAO().getAllLocations();

        Map<String, Double> P_loc_wifi = new HashMap<String, Double>();
        for(String location : locations) {
            P_loc_wifi.put(location, 1.0);
        }

        // For every scan
        for(Scan scan : scans) {
            // get MAC and RSSi
            String curMAC = scan.getMAC();
            int curRSSi = scan.getRSSi();
            // Get all location tables

            for(MacTable mactable : MacTables){
                // TODO: Change into
                Map<String, Map<Integer, Integer>> MACs = new HashMap<String, Map<Integer, Integer>>();
                // If the scanned MAC exists in this location table (GET ROW)
                if(MACs.containsKey(curMAC)) {
                    // Get the RSSis for the MAC (GET COLUMNS)
                    Map<Integer, Integer> freqsRSSi = MACs.get(curMAC);
                    // Check if the RSSi value exists (GET COLUMN)
                    if(freqsRSSi.containsKey(curRSSi)) {
                        // Get probability of seeing this RSSi for this RSSi (MUST BE DOUBLE??)
                        Double prob = freqsRSSi.get(curRSSi) / freqsRSSi.size();
                        P_loc_wifi.put("A", P_loc_wifi.get("A") * prob);
                    }
                } else {
                    // Remove this from the possible location list or keep it in?
                }
            }
        }

        // Return Location with highest probability
        String curLocation = "None";
        Double maxProb = 0.0;
        Iterator it = P_loc_wifi.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            System.out.println(pair.getKey() + " = " + pair.getValue());
            if(pair.getValue() > maxProb){
                curLocation = pair.getKey();
            }
            it.remove(); // avoids a ConcurrentModificationException
        }

        return curLocation;
    }

}
