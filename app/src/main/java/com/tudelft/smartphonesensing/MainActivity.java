package com.tudelft.smartphonesensing;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity  {

    final Fragment fragment1 = new ManageFragment();
    final Fragment fragment2 = new TrainFragment();
    final Fragment fragment3 = new TestFragment();
    final FragmentManager fm = getSupportFragmentManager();
    Fragment active = fragment1;
    public String cell = "C1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView navView = findViewById(R.id.bottomNavigationView);
        fm.beginTransaction().add(R.id.main_container, fragment3, "3").hide(fragment3).commit();
        fm.beginTransaction().add(R.id.main_container, fragment2, "2").hide(fragment2).commit();
        fm.beginTransaction().add(R.id.main_container,fragment1, "1").commit();
        navView.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_manage:
                    fm.beginTransaction().hide(active).show(fragment1).commit();
                    active = fragment1;
                    return true;

                case R.id.navigation_train:
                    fm.beginTransaction().hide(active).show(fragment2).commit();
                    active = fragment2;
                    return true;

                case R.id.navigation_test:
                    fm.beginTransaction().hide(active).show(fragment3).commit();
                    active = fragment3;
                    return true;
            }
            return false;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.loc_pick_menu, menu);
        return true;
    }

    // TODO: dynamically generate buttons when choosing cells!
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case R.id.btn_C1:
                this.cell = "C1";
                Toast.makeText(getApplicationContext(),"Train location set to Cell 1", Toast.LENGTH_SHORT).show();
                return true;

            case R.id.btn_C2:
                this.cell = "C2";
                Toast.makeText(getApplicationContext(),"Train location set to Cell 2", Toast.LENGTH_SHORT).show();
                return true;

            case R.id.btn_C3:
                this.cell = "C3";
                Toast.makeText(getApplicationContext(),"Train location set to Cell 3", Toast.LENGTH_SHORT).show();
                return true;

            case R.id.btn_C4:
                this.cell = "C4";
                Toast.makeText(getApplicationContext(),"Train location set to Cell 4", Toast.LENGTH_SHORT).show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public String getCell() {
        return cell;
    }

    //    @Override
//    public void onClick(View v) {
//        switch(v.getId()){
//            case R.id.fab:
//                //check permissions
//                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_ACCESS_FINE_LOCATION);
//                }
//                beginWifiScanAndShowGraph();
//
//                break;
//            default: break;
//
//        }
//

}
