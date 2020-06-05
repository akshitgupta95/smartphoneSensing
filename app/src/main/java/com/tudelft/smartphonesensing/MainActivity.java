package com.tudelft.smartphonesensing;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    final Fragment manageFragment = new ManageFragment();
    final Fragment testFragment = new TestFragment();
    final Fragment floorplanFragment = new FloorplanFragment();
    final FragmentManager fm = getSupportFragmentManager();

    public Fragment getActiveFragment() {
        return getSupportFragmentManager()
                .findFragmentById(R.id.main_container);
//        return active;
    }

    public void setActiveFragment(Fragment active) {
        this.active = active;
    }

    Fragment active = manageFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView navView = findViewById(R.id.bottomNavigationView);
        fm.beginTransaction().add(R.id.main_container, testFragment, "2").hide(testFragment).commit();
        fm.beginTransaction().add(R.id.main_container, floorplanFragment, "3").hide(floorplanFragment).commit();
        fm.beginTransaction().add(R.id.main_container, manageFragment, "1").commit();
        navView.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_train:
                    Log.v("Fragment", getActiveFragment().toString());
                    fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    fm.beginTransaction().hide(testFragment).commit();
                    fm.beginTransaction().hide(floorplanFragment).commit();
                    fm.beginTransaction().hide(getActiveFragment()).show(manageFragment).commit();
                    active = manageFragment;
                    return true;

                case R.id.navigation_floorplan:
                    Log.v("Fragment", getActiveFragment().toString());
                    fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    fm.beginTransaction().hide(testFragment).commit();
                    fm.beginTransaction().hide(manageFragment).commit();
                    fm.beginTransaction().hide(getActiveFragment()).show(floorplanFragment).commit();
                    active = floorplanFragment;
                    return true;

                case R.id.navigation_test:
                    Log.v("Fragment", getActiveFragment().toString());
                    fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    fm.beginTransaction().hide(floorplanFragment).commit();
                    fm.beginTransaction().hide(manageFragment).commit();
                    fm.beginTransaction().hide(getActiveFragment()).show(testFragment).commit();
                    active = testFragment;
                    return true;
            }
            return false;
        }
    };
}
