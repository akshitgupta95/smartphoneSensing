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
    ModelState model = MainActivity.modelState;
    FloorplanView floorview;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.floorplan_fragment, container, false);
    }

    private Consumer<Floorplan> floorchange = (floor) -> {
        floorview.floorplanChanged();
    };
    private Consumer<Void> predictionUpdate = (nil) -> {
        floorview.invalidate();
    };

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button editButton = this.getView().findViewById(R.id.floorplanEditButton);
        Button particlesButton = this.getView().findViewById(R.id.floorplanParticlesButton);
        Button loadButton = this.getView().findViewById(R.id.floorplanLoadButton);
        LinearLayout buttonContainer = this.getView().findViewById(R.id.floorplanButtons);
        floorview = this.getView().findViewById(R.id.floorplanView);

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

            model.setRunning(mode == FloorplanView.SelectionMode.PARTICLES);
            floorview.setSelectionMode(mode);
        };

        editButton.setOnClickListener(btn -> clickMode.accept(FloorplanView.SelectionMode.EDITING));
        particlesButton.setOnClickListener(btn -> clickMode.accept(FloorplanView.SelectionMode.PARTICLES));
        loadButton.setOnClickListener(btn -> MainActivity.modelState.selectFloorMenu());

        //listen for model events
        model.floorplanChange.listen(floorchange);
        model.predictionUpdate.listen(predictionUpdate);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        //remove listeners to prevent mem leak
        model.floorplanChange.remove(floorchange);
        model.predictionUpdate.remove(predictionUpdate);
    }
}
