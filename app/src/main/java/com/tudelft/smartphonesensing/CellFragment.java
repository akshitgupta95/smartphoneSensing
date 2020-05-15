package com.tudelft.smartphonesensing;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.NumberPicker;
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
import java.util.stream.Stream;

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
        final AppDatabase db = Room.databaseBuilder(this.getContext().getApplicationContext(), AppDatabase.class, "production")
                .allowMainThreadQueries()
                .build();

        locationScans = db.scanDAO().getAllScansLoc(selectedCell);
        cellnameText.setText(selectedCell);

        HashMap<String, Integer> macHistogram = new HashMap<>();
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
        List<Map.Entry<String, Integer>> sortedmacHistogram = macHistogram.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .collect(Collectors.toList());
        final int maxgausses = 5;
        for (int i = 0; i < maxgausses && i < sortedmacHistogram.size(); i++) {
            Map.Entry<String, Integer> entry = sortedmacHistogram.get(i);
            Bayes.MacLocProbability probs = new Bayes.MacLocProbability(locationScans, entry.getKey());

            //TODO rewrite this a bit and pre-allocate instead and pull the constants out
            List<DataPoint> linedata = new ArrayList<>();
            for (double x = 0; x < 10; x += 0.05) {
                linedata.add(new DataPoint(x, probs.sampleGaussian(x)));
            }
            LineGraphSeries<DataPoint> line = new LineGraphSeries<DataPoint>(linedata.toArray(new DataPoint[0]));
            rssiGraph.addSeries(line);
            //TODO probly want the ssid here instead of mac
            line.setTitle(entry.getKey());
            int color = 0xff000000;//alpha
            color += Math.round(Math.random() * 255) << 16;//red
            color += Math.round(Math.random() * 255) << 8;//green
            color += Math.round(Math.random() * 255);//blue
            line.setColor(color);
        }
        rssiGraph.setTitle("Probability density (y) vs normalized signal level (x)");
        rssiGraph.getLegendRenderer().setVisible(true);
        rssiGraph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.cell_fragment, container, false);
    }

    private void onScan(List<ScanResult> results) {
        scanProgressbar.setProgress(activeScan.getProgress(), true);

        final AppDatabase db = Room.databaseBuilder(getActivity(), AppDatabase.class, "production")
                .allowMainThreadQueries()
                .build();

        if (results.size() != 0) {
            for (ScanResult scanResult : results) {
                int normlevel = WifiManager.calculateSignalLevel(scanResult.level, 10);
                Scan result = new Scan(scanResult.BSSID, scanResult.SSID, scanResult.level, normlevel, scanResult.frequency, selectedCell, scanResult.timestamp);
                db.scanDAO().InsertAll(result);
                Log.v("DB", "Added scan: " + result);
            }
        }
        drawSignaldata();
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

        this.getView().findViewById(R.id.cellbutton0).setOnClickListener(v -> setCell("cell0"));
        this.getView().findViewById(R.id.cellbutton1).setOnClickListener(v -> setCell("cell1"));
    }
}
