package com.tudelft.smartphonesensing;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.Collections;
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
        private Map<Long, GaussianSampler> table = new HashMap<>();

        LocationMacTable(String location) {
            this.location = location;
        }

        double sampleProb(long mac, double rssi) {
            GaussianSampler sampler = table.get(mac);
            return (sampler == null ? 0 : sampler.sample(rssi));
        }

        void addMacData(long mac, List<Scan> scandata) {
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
        final AppDatabase db = AppDatabase.getInstance(context);

        locationMacTables.clear();
        List<String> locations = db.scanDAO().getAllLocations();
        for (String location : locations) {
            List<Long> locMacs = db.scanDAO().getAllMacsAtLocation(location);
            LocationMacTable subtable = new LocationMacTable(location);
            for (Long mac : locMacs) {
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

        //TODO the value of this depends on the width of the expected RSSi range!!
        //it will break when a different normalisation is used than 0-10
        final double minimalP = 0.1;

        //TODO: Find good threshold value to use
        final double threshold= 0.7;
        //sort here and do iterations
        Collections.sort(scanResults,(o1, o2) -> o1.level - o2.level);
        outerLoop: for (ScanResult scan : scanResults) {
            long curMAC = Util.macStringToLong(scan.BSSID);
            double curLevel = wifiManager.calculateSignalLevel(scan.level, 10);

            for (cellCandidate cand : candidateList) {
                double sampledP = Math.max(minimalP, cand.macTable.sampleProb(curMAC, curLevel));
                cand.probability *= sampledP;
                if(cand.probability>threshold)
                    break outerLoop;
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
