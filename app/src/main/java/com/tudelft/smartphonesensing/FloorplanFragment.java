package com.tudelft.smartphonesensing;

import android.graphics.RectF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class FloorplanFragment extends Fragment {

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
        FloorplanView floorview = this.getView().findViewById(R.id.floorplanView);


        addRectButton.setOnClickListener(btn -> {
            floorview.addRectangleObstacle(0, 0);
        });

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

        editButton.setOnClickListener(btn -> {
            boolean editing = floorview.getEditing();
            floorview.setEditing(!editing);
            editButton.setText(editing ? "Edit" : "Done");
        });
    }

}
