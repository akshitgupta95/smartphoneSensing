package com.tudelft.smartphonesensing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

public class ManageFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemClickListener {


    private RecyclerView recyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager layoutManager;
    private FloatingActionButton addCellFab;
    ArrayList<String> data = new ArrayList<>();
    int startNaming = 'A';

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.manage_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        final AppDatabase db = Room.databaseBuilder(getContext().getApplicationContext(), AppDatabase.class, "production")
                .allowMainThreadQueries()
                .build();
        data.clear();

        //TODO currently cells without data are lost when reopening the app, maybe second table with all locations?
        data.addAll(db.scanDAO().getAllLocations());

        recyclerView = getView().findViewById(R.id.cells_rv);
        addCellFab = getView().findViewById(R.id.addCell);
        addCellFab.setOnClickListener(this);
        setupRv();
    }

    private void setupRv() {
        layoutManager = new LinearLayoutManager(getActivity().getApplicationContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        mAdapter = new CellsAdapter(data, this);
        recyclerView.setAdapter(mAdapter);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.addCell:
                addCell();
                break;
            default:
                break;
        }
    }

    private void addCell() {
        //TODO: generate a popup asking user for name of cell
        data.add(Character.toString((char) startNaming));
        startNaming++;
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        //TODO remove?
        //String cellName=mAdapter.getItem(i);
    }
}
