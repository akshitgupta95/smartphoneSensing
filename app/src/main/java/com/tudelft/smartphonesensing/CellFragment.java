package com.tudelft.smartphonesensing;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.room.Room;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CellFragment extends Fragment {

    private TextView numMacsText;
    private TextView numSamplesText;
    private TextView cellnameText;
    private Button scanButton1x;
    private Button scanButton5x;
    private Button scanButton20x;
    private ProgressBar scanProgressbar;
    private GraphView rssiGraph;

    private String selectedCell = null;
    private List<Scan> locationScans;

    private WifiScanner activeScan = null;

    void setCell(String locid) {
        selectedCell = locid;
        drawSignaldata();
    }

    void drawSignaldata() {
        //TODO should we use some global instance of the database?
        final AppDatabase db = AppDatabase.getInstance(this.getContext());

        locationScans = db.scanDAO().getAllScansAtLocation(selectedCell);
        cellnameText.setText(selectedCell);

        HashMap<Long, Integer> macHistogram = new HashMap<>();
        for (Scan scan : locationScans) {
            Integer prev = macHistogram.get(scan.getMAC());
            macHistogram.put(scan.getMAC(), (prev == null ? 1 : prev + 1));
        }

        int maxMacEntries = 0;
        int minMacEntries = Integer.MAX_VALUE;
        for (Integer n : macHistogram.values()) {
            maxMacEntries = Math.max(maxMacEntries, n);
            minMacEntries = Math.min(minMacEntries, n);
        }
        if (minMacEntries == Integer.MAX_VALUE) {
            minMacEntries = 0;
        }
        numMacsText.setText(macHistogram.size() + "");
        numSamplesText.setText(maxMacEntries + "");

        rssiGraph.removeAllSeries();
        List<Map.Entry<Long, Integer>> sortedmacHistogram = macHistogram.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .collect(Collectors.toList());
        final int maxgausses = 5;
        for (int i = 0; i < maxgausses && i < sortedmacHistogram.size(); i++) {
            Map.Entry<Long, Integer> entry = sortedmacHistogram.get(i);

            List<Scan> filteredScans = locationScans.stream()
                    .filter(s -> s.getMAC() == entry.getKey())
                    .collect(Collectors.toList());
            Bayes.GaussianSampler probs = new Bayes.GaussianSampler(filteredScans);

            //TODO rewrite this a bit and pre-allocate instead and pull the constants out
            List<DataPoint> linedata = new ArrayList<>();
            for (double x = 0; x < 10; x += 0.05) {
                linedata.add(new DataPoint(x, probs.sample(x)));
            }
            LineGraphSeries<DataPoint> line = new LineGraphSeries<DataPoint>(linedata.toArray(new DataPoint[0]));
            Scan scanentry = locationScans.stream()
                    .filter(s -> s.getMAC() == entry.getKey())
                    .findFirst()
                    .orElse(null);

            String name;
            if (scanentry != null && !scanentry.getSSID().isEmpty()) {
                name = scanentry.getSSID();
            } else {
                name = String.format("[%s]", Util.macLongToString(entry.getKey()));
            }
            line.setTitle(name);

            //Generate a color based on the SSID that will be random, but the same every time
            int namehash = entry.getKey().hashCode();
            //reuse some entropy to fill all the bits (not ideal)
            namehash = namehash ^ (namehash << 12);
            //set alpha channel to 255 (opaque) and add random rgb
            int color = 0xff000000 | (namehash & 0xffffff);
            line.setColor(color);
            rssiGraph.addSeries(line);
        }
        rssiGraph.getLegendRenderer().resetStyles();
        rssiGraph.setTitle("Probability density (y) vs normalized signal level (x)");
        rssiGraph.getLegendRenderer().setVisible(true);
        rssiGraph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);
    }

    //TODO make sensible code path for this
    private String initialCell;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Bundle bundle = this.getArguments();
        if (bundle != null) {
            initialCell = bundle.get("cellName").toString();
        }
        return inflater.inflate(R.layout.cell_fragment, container, false);
    }

    private List<ScanResult> oldResults;

    private void onScan(List<ScanResult> results) {
        scanProgressbar.setProgress(activeScan.getProgress(), true);

        final AppDatabase db = Room.databaseBuilder(getActivity(), AppDatabase.class, "production")
                .allowMainThreadQueries()
                .build();

        if(checkUnique(results)) { //compare to previous results and see if any one elements differs
            for (ScanResult scanResult : results) {
                //TODO: Improve the normalisation technique
                int normlevel = WifiManager.calculateSignalLevel(scanResult.level, 10);
                //TODO: only store gaussian mean and std in DB
                Scan result = new Scan(Util.macStringToLong(scanResult.BSSID), scanResult.SSID, scanResult.level, normlevel, scanResult.frequency, selectedCell, scanResult.timestamp);
                db.scanDAO().InsertAll(result);
                Log.v("DB", "Added scan: " + result);
            }
            oldResults = results;
            drawSignaldata();
        }

    }

    private boolean checkUnique(List<ScanResult> results){
        boolean isUnique=false;
        for(int i=0;i<results.size();i++){
            //either bssid or level is different
            if(!results.get(i).BSSID.equals(oldResults.get(i).BSSID)){
                isUnique=true;
            }
            else if(results.get(i).level != oldResults.get(i).level){
                isUnique=true;
            }
        }
        return isUnique;

    }


    private void onScanFinished(List<List<ScanResult>> allresults) {
        activeScan = null;
        scanProgressbar.setVisibility(View.INVISIBLE);
        //TODO ui stuff
    }

    private void onScanError() {
        //TODO ui stuff
    }

    private void scanClicked(int numscans) {
        if (this.activeScan != null) {
            //TODO add toast about scanning already
            return;
        }
        if (this.selectedCell == null) {
            //TODO add toast about this even though this should never happen
            return;
        }
        if (!WifiScanner.ensurePermission(getActivity())) {
            //TODO auto-trigger button agaain after permission has been given?
        }
        this.activeScan = new WifiScanner(getContext(), numscans, this::onScan, this::onScanFinished, this::onScanError);
        scanProgressbar.setProgress(0, false);
        scanProgressbar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        numMacsText = this.getView().findViewById(R.id.numMacs);
        numSamplesText = this.getView().findViewById(R.id.numSamples);
        cellnameText = this.getView().findViewById(R.id.cellname);
        scanButton1x = this.getView().findViewById(R.id.scanButton1x);
        scanButton5x = this.getView().findViewById(R.id.scanButton5x);
        scanButton20x = this.getView().findViewById(R.id.scanButton20x);
        scanProgressbar = this.getView().findViewById(R.id.scanProgressBar);
        rssiGraph = this.getView().findViewById(R.id.rssiGraph);

        scanProgressbar.setVisibility(View.INVISIBLE);

        scanButton1x.setOnClickListener(btn -> this.scanClicked(1));
        scanButton5x.setOnClickListener(btn -> this.scanClicked(5));
        scanButton20x.setOnClickListener(btn -> this.scanClicked(20));

        //TODO move this elsewhere
        setCell(initialCell);
    }
}
