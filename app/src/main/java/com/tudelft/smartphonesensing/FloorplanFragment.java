package com.tudelft.smartphonesensing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONException;

import java.util.List;
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
        Button particlesButton = this.getView().findViewById(R.id.floorplanParticlesButton);
        Button loadButton = this.getView().findViewById(R.id.floorplanLoadButton);
        LinearLayout buttonContainer = this.getView().findViewById(R.id.floorplanButtons);
        FloorplanView floorview = this.getView().findViewById(R.id.floorplanView);
        floorview.setParticleModel(model);

        Runnable buttonsChanged = () -> {
            buttonContainer.removeAllViews();
            List<Floorplan.ElementAction> actions = floorview.getActions();
            for (Floorplan.ElementAction action : actions) {
                Button btn = new Button(getContext());
                btn.setText(action.getName());
                btn.setOnClickListener(v -> action.click());
                buttonContainer.addView(btn);
            }
        };

        floorview.addButtonListener(buttonsChanged);
        buttonsChanged.run();

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
                model.setNorthAngleOffset(map.getNorthAngleOffset());
                model.spawnParticles(10000);
                floorview.invalidate();
            }
            floorview.setSelectionMode(mode);
        };

        editButton.setOnClickListener(btn -> clickMode.accept(FloorplanView.SelectionMode.EDITING));
        particlesButton.setOnClickListener(btn -> clickMode.accept(FloorplanView.SelectionMode.PARTICLES));

        loadButton.setOnClickListener(btn -> {
            AppDatabase db = AppDatabase.getInstance(getContext());
            List<FloorplanDataDAO.FloorplanMeta> meta = db.floorplanDataDAO().getAllNames();

            String[] names = meta.stream().map(e -> e.name == null ? "no name" : e.name).toArray(String[]::new);
            Util.showDropdownSpinner(getContext(), "Open floorplan", names, index -> {
                FloorplanDataDAO.FloorplanMeta choice = meta.get(index);
                try {
                    Floorplan floor = Floorplan.load(db, db.floorplanDataDAO().getById(choice.id));
                    floorview.setFloorplan(floor);
                } catch (JSONException e) {
                    Toast.makeText(getContext(), "Failed to load floorplan", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
