package com.tudelft.smartphonesensing;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestFragment extends Fragment implements View.OnClickListener {

    private WifiManager wifiManager;
    private List<ScanResult> results;
    private Bayes bayes;
    private TextView normaliseTV;
    private ProgressBar progressBar;
    private float normalisationGain=0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.test_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FloatingActionButton pred = (FloatingActionButton) getView().findViewById(R.id.pred);
        pred.setOnClickListener(this);
        FloatingActionButton normalise = (FloatingActionButton) getView().findViewById(R.id.normalise);
        normalise.setOnClickListener(this);
        normaliseTV=(TextView) getView().findViewById(R.id.normaliseStatus);
        progressBar=getView().findViewById(R.id.progress_loader);
        bayes = new Bayes(getActivity());
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.pred:
//                bayes = new Bayes(getActivity());
                // TODO: only create table when database is updated so prediction is faster
                bayes.generateTables();

                beginWifiScanAndLocate();
                break;
            case R.id.normalise:
                progressBar.setVisibility(View.VISIBLE);
                Snackbar.make(progressBar, "Beginning Normalisation with 3 scans", Snackbar.LENGTH_LONG)
                        .show();
                bayes.generateTables();
                beginNormalisation();
                break;
            default:
                break;
        }
    }

    private void beginNormalisation() {
        startScanAndCompareReadings();

    }

    private void startScanAndCompareReadings() {

        WifiScanner scanner = new WifiScanner(getContext(), 3, null, this::parseUniqueResultsAndFindProximateLocations, this::scanFailure);
    }

    private void parseUniqueResultsAndFindProximateLocations(List<List<ScanResult>> lists) {
        progressBar.setVisibility(View.GONE);
        String text="numOfScans(m): "+lists.size()+"\n"; //should be 3
        List<ScanResult> uniqueResults=lists.get(0);
        for(ScanResult scan: uniqueResults)
            scan.frequency=1;
        for(int j=1;j<lists.size();j++){
            List<ScanResult> list=lists.get(j);
            for (int k=0;k<list.size();k++){
                ScanResult result=list.get(k);
                Boolean flag=false;
                for(int m=0;m<uniqueResults.size();m++){
                    if(uniqueResults.get(m).BSSID.equals(result.BSSID)){
                        flag=true;
                        int levelSum=(uniqueResults.get(m).level+result.level);
                        uniqueResults.get(m).level=levelSum;
                        uniqueResults.get(m).frequency++;
                    }
                }
                if(!flag) {
                    result.frequency = 1;
                    uniqueResults.add(result);
                }
                }

            }
        for(ScanResult scan: uniqueResults)
        {
            scan.level=scan.level/scan.frequency;
        }
        uniqueResults.sort((a, b) -> Integer.compare(b.level,a.level));
        //TODO: If needed, we can remove low RSSi values from unique results to always have convergence
        List<Integer> G=new ArrayList<>();
        boolean normalised=false;
        //find same macs in training data
        List<Bayes.LocationMacTable> locationMacTables=bayes.getLocationMacTables();
        for(Bayes.LocationMacTable locationMacTable: locationMacTables){
            Map<Long, Bayes.GaussianSampler> macsInLocation=locationMacTable.getTable();
            boolean found=true;
            //try to normalise only if this location contains all unique results
            for(int i=0;i<uniqueResults.size();i++){
                if(!macsInLocation.containsKey(Util.macStringToLong(uniqueResults.get(i).BSSID)))
                    found= false;
            }
            //this location contains all SSIDs, so a candidate for normalisation
            if(found) {
                boolean checkNormalise=checkForNormalisationAndNormalise(macsInLocation, uniqueResults,G);
                if(!normalised)
                normalised=checkNormalise;
                if (checkNormalise) {
                   Toast.makeText(getContext(), "Normalisation was done using location: "+locationMacTable.location, Toast.LENGTH_SHORT).show();

                }
            }

        }
        if(normalised) {
            float Gsum = 0;
            for (int g : G) {
                Gsum += g;
            }
            float Gmean = Gsum / G.size();
            normalisationGain=Gmean;
            Toast.makeText(getContext(), "Normalisation Gain: "+Gmean, Toast.LENGTH_SHORT).show();

        }
//        normaliseStep2(uniqueResults);

        }

    private Boolean checkForNormalisationAndNormalise(Map<Long, Bayes.GaussianSampler> macsInLocation, List<ScanResult> uniqueResults, List<Integer> G) {
        int threshold=3;
        List<Integer> V1=new ArrayList<>();
        for(int i=0;i<uniqueResults.size();i++){
            int norm1=WifiManager.calculateSignalLevel(uniqueResults.get(i).level, 46);
            int norm2=WifiManager.calculateSignalLevel(uniqueResults.get(0).level, 46);
            V1.add(norm1-norm2);
        }
        List<Integer> V2=new ArrayList<>();
        for(int i=0;i<uniqueResults.size();i++){
            long mac0=Util.macStringToLong(uniqueResults.get(0).BSSID);
            long mac=Util.macStringToLong(uniqueResults.get(i).BSSID);
            V2.add((int)(macsInLocation.get(mac).mean-macsInLocation.get(mac0).mean));
        }
        int normaliseSum=0;
        for(int i=0;i<V1.size();i++){
            normaliseSum+= Math.abs(V1.get(i)-V2.get(i));
        }
        normaliseSum=normaliseSum/V1.size();
        if(normaliseSum<=threshold){
            for(int i=0;i<uniqueResults.size();i++){
                int p1=WifiManager.calculateSignalLevel(uniqueResults.get(i).level, 46);
                int p2=(int)macsInLocation.get(Util.macStringToLong(uniqueResults.get(i).BSSID)).mean;
                G.add(p1-p2);
            }

            return true;
        }
        return false;
    }


    private void normaliseStep2(List<ScanResult> results) {
//        TODO: add 0 check on results
        //
        String text="SIZE(m): "+results.size()+"\n";
        //the results list is sorted in decreasing order of signal values
//        results.sort((a, b) -> a.SSID.compareTo(b.SSID));
//      Map:(BSSIDx-BSSID1,RSSI Diff)
//        V =<0,p2 −p2 ,···,p2 −p2 > from paper
        HashMap<String,Integer> VectorV= new HashMap<>();
        String BSSID1=String.valueOf(Util.macStringToLong(results.get(0).BSSID));
        int rssiatOne=results.get(0).level;
        for(int i=0;i<results.size();i++) {
            String bssid=String.valueOf(Util.macStringToLong(results.get(i).BSSID));
            bssid=bssid+"-"+BSSID1;
            int deltaRssi=results.get(i).level-rssiatOne;
            VectorV.put(bssid,deltaRssi);
        }
        for(Map.Entry<String, Integer> entry:VectorV.entrySet() ){
            text+=entry.getKey()+": "+entry.getValue()+"\n";
        }

        normaliseTV.setText(text);
    }

    // Remove this it rebuilds the table after the app is opened again.
    @Override
    public void onResume() {
        super.onResume();
        //TODO trigger table cache purge
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
            List<Bayes.cellCandidate> candidates = bayes.predictLocation(results,normalisationGain);
            Bayes.cellCandidate best = candidates.get(0);

            // display the location
            Toast.makeText(this.getContext(), "Probability :" + best.probability, Toast.LENGTH_SHORT).show();
            TextView locationText = (TextView) getView().findViewById(R.id.text_loc);
            locationText.setText(best.macTable.location);

            String debugtext = "";
            for (Bayes.cellCandidate cand : candidates) {
                debugtext += String.format("%.4f %s\n", cand.probability, cand.macTable.location);
            }
            TextView debugview = getView().findViewById(R.id.locateDebugOutput);
            debugview.setText(debugtext);
        }
    }

    private void scanFailure() {
        // handle failure: new scan did NOT succeed
        // consider using old scan results: these are the OLD results!
        Toast.makeText(getActivity().getApplicationContext(), "failed to scan", Toast.LENGTH_SHORT).show();
        this.results = wifiManager.getScanResults();
    }
}
