package com.tudelft.smartphonesensing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ManageFragment extends Fragment {

    private NumberPicker numCellsPicker;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        return inflater.inflate(R.layout.manage_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        numCellsPicker=getView().findViewById(R.id.numPicker);
        numCellsPicker.setMaxValue(10);
        numCellsPicker.setMinValue(0);


    }
}
