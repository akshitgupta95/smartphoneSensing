package com.tudelft.smartphonesensing;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

import androidx.room.Room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Bayes {

    Context context;
    List<LocationMacTable> locationMacTables = new ArrayList<>();
    List<String> usableMacs = new ArrayList<>();

    public Bayes(Context context) {
        this.context = context;
    }

    static public class LocationMacTable {
        String location;
        //mac -> sampler map
        private Map<String, GaussianSampler> table = new HashMap<>();

        LocationMacTable(String location) {
            this.location = location;
        }

        double sampleProb(String mac, double rssi) {
            GaussianSampler sampler = table.get(mac);
            return (sampler == null ? 0 : sampler.sample(rssi));
        }

        void addMacData(String mac, List<Scan> scandata) {
            table.put(mac, new GaussianSampler(scandata));
        }
    }

    static public class GaussianSampler {
        double mean;
        double stddev;
        int nsamples;
        List<Scan> scans;

        public double getMean() {
            return mean;
        }

        public double getStdDev() {
            return stddev;
        }

        public List<Scan> getScans() {
            return scans;
        }

        /**
         * Samples the pure gaussian pdf generated from the know data points
         *
         * @param rssi signal level to sample at
         * @return probability density
         */
        public double sample(double rssi) {
            if (nsamples == 0) {
                return 0;
            }
            //TODO find better solution to deal with stddev==0 and remove magic constant
            double roundedStd = Math.max(stddev, 0.2);
            return (1 / (roundedStd * Math.sqrt(2 * Math.PI))) * Math.exp(-(rssi - mean) * (rssi - mean) / (2 * roundedStd * roundedStd));
        }

        public GaussianSampler(List<Scan> scans) {
            if (scans.size() < 2) {
                mean = 0;
                stddev = 0;
                nsamples = 0;
            } else {
                nsamples = scans.size();
                double levelsum = 0;
                for (Scan scan : scans) {
                    levelsum += scan.getLevel();
                }
                mean = levelsum / nsamples;
                double leveldiffsum = 0;
                for (Scan scan : scans) {
                    double diff = scan.getLevel() - mean;
                    leveldiffsum += diff * diff;
                }
                stddev = Math.sqrt(leveldiffsum / (nsamples - 1));
            }
        }
    }

    public void generateTables() {
        final AppDatabase db = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "production")
                .allowMainThreadQueries()
                .build();

        locationMacTables.clear();
        List<String> locations = db.scanDAO().getAllLocations();
        for (String location : locations) {
            List<String> locMacs = db.scanDAO().getAllMacsAtLocation(location);
            LocationMacTable subtable = new LocationMacTable(location);
            for (String mac : locMacs) {
                List<Scan> macAppearences = db.scanDAO().getAllScansWithMacAndLocation(location, mac);
                subtable.addMacData(mac, macAppearences);
            }
            locationMacTables.add(subtable);
        }
    }

    public static class cellCandidate {
        double probability;
        LocationMacTable macTable;

        public cellCandidate(double p, LocationMacTable table) {
            probability = p;
            macTable = table;
        }
    }

    public List<cellCandidate> predictLocation(List<ScanResult> scanResults) {
        //TODO locationlist could be empty, show a message and quit
        //initialize set of candidates with equal probabilities
        //no need to normalize yet
        double baseprob = 1.0;
        List<cellCandidate> candidateList = locationMacTables.stream()
                .map(l -> new cellCandidate(baseprob, l))
                .collect(Collectors.toList());

        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);


        for (ScanResult scan : scanResults) {
            String curMAC = scan.BSSID;
            int curLevel = wifiManager.calculateSignalLevel(scan.level, 10);

            for (cellCandidate cand : candidateList) {
                double sampledP = Math.max(0.1, cand.macTable.sampleProb(curMAC, curLevel));
                if (Double.isNaN(sampledP)) {
                    Log.v("err", "ads");
                }
                cand.probability *= sampledP;
            }
        }

        //normalize probabilities so they add up to 1
        double psum = 0;
        for (cellCandidate cand : candidateList) {
            psum += cand.probability;
        }
        for (cellCandidate cand : candidateList) {
            cand.probability /= psum;
        }

        candidateList.sort((a, b) -> Double.compare(b.probability, a.probability));
        return candidateList;
    }
}
