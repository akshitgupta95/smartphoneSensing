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

    final Fragment fragment1 = new ManageFragment();
    final Fragment fragment2 = new TestFragment();
    final FragmentManager fm = getSupportFragmentManager();

    public Fragment getActiveFragment() {
        return getSupportFragmentManager()
                .findFragmentById(R.id.main_container);
//        return active;
    }

    public void setActiveFragment(Fragment active) {
        this.active = active;
    }

    Fragment active = fragment1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView navView = findViewById(R.id.bottomNavigationView);
        fm.beginTransaction().add(R.id.main_container, fragment2, "2").hide(fragment2).commit();
        fm.beginTransaction().add(R.id.main_container, fragment1, "1").commit();
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
                    fm.beginTransaction().hide(fragment2).commit();
                    fm.beginTransaction().hide(getActiveFragment()).show(fragment1).commit();
                    active = fragment1;
                    return true;

                case R.id.navigation_test:
                    Log.v("Fragment", getActiveFragment().toString());
                    fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    fm.beginTransaction().hide(fragment1).commit();
                    fm.beginTransaction().hide(getActiveFragment()).show(fragment2).commit();
                    active = fragment2;
                    return true;
            }
            return false;
        }
    };
}
