package com.tudelft.smartphonesensing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.function.Consumer;

public class FloorplanFragment extends Fragment {
    ParticleModel model = new ParticleModel();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.floorplan_fragment, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button editButton = this.getView().findViewById(R.id.floorplanEditButton);
        Button saveButton = this.getView().findViewById(R.id.floorplanSaveButton);
        Button loadButton = this.getView().findViewById(R.id.floorplanLoadButton);
        Button addRectButton = this.getView().findViewById(R.id.floorplanAddRectButton);
        Button particlesButton = this.getView().findViewById(R.id.floorplanParticlesButton);
        Button simButton = this.getView().findViewById(R.id.floorplanSimButton);
        FloorplanView floorview = this.getView().findViewById(R.id.floorplanView);
        floorview.setParticleModel(model);

        Consumer<FloorplanView.SelectionMode> clickMode = (mode) -> {
            FloorplanView.SelectionMode currentmode = floorview.getSelectionMode();
            if (mode == currentmode) {
                mode = FloorplanView.SelectionMode.VIEWING;
            }
            editButton.setText(mode == FloorplanView.SelectionMode.EDITING ? "Done" : "Edit");
            particlesButton.setText(mode == FloorplanView.SelectionMode.PARTICLES ? "Done" : "Particles");

            if (mode == FloorplanView.SelectionMode.PARTICLES) {
                Floorplan map = floorview.getFloorplan();
                model.setBoxes(map.getWalkable());
                model.spawnParticles(10000);
                floorview.invalidate();
            }
            floorview.setSelectionMode(mode);
            addRectButton.setVisibility(mode == FloorplanView.SelectionMode.EDITING ? View.VISIBLE : View.INVISIBLE);
            simButton.setVisibility(mode == FloorplanView.SelectionMode.PARTICLES ? View.VISIBLE : View.INVISIBLE);
        };

        addRectButton.setOnClickListener(btn -> floorview.addRectangleObstacle());
        editButton.setOnClickListener(btn -> clickMode.accept(FloorplanView.SelectionMode.EDITING));
        particlesButton.setOnClickListener(btn -> clickMode.accept(FloorplanView.SelectionMode.PARTICLES));

        saveButton.setOnClickListener(btn -> {
            AppDatabase db = AppDatabase.getInstance(getContext());
            FloorplanMetaDAO.FloorplanMeta meta = new FloorplanMetaDAO.FloorplanMeta();
            meta.setFloorplan(floorview.getFloorplan());
            db.floorplanMetaDAO().InsertAll(meta);
        });

        loadButton.setOnClickListener(btn -> {
            AppDatabase db = AppDatabase.getInstance(getContext());
            FloorplanMetaDAO.FloorplanMeta meta = db.floorplanMetaDAO().getLast();
            floorview.setFloorplan(meta.getFloorplan());
        });

        simButton.setOnClickListener(btn -> {
            boolean issim = floorview.getSimulating();
            floorview.setSimulating(!issim);
            simButton.setText(!issim ? "Stop" : "Simulate");
        });
    }
}
