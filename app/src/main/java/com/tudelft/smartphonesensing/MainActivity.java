package com.tudelft.smartphonesensing;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    final Fragment manageFragment = new ManageFragment();
    final Fragment testFragment = new TestFragment();
    final Fragment floorplanFragment = new FloorplanFragment();
    final Fragment cellFragment = new CellFragment();
    final FragmentManager fm = getSupportFragmentManager();
    final Fragment[] tabbedFragments = new Fragment[]{manageFragment, testFragment, floorplanFragment};

    public Fragment getActiveFragment() {
        return getSupportFragmentManager()
                .findFragmentById(R.id.main_container);
//        return active;
    }

    public void setActiveFragment(Fragment active) {
        FragmentTransaction trans = fm.beginTransaction();
        Arrays.stream(tabbedFragments).forEach(trans::hide);
        trans.show(active);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView navView = findViewById(R.id.bottomNavigationView);
        FragmentTransaction trans=fm.beginTransaction();
        Arrays.stream(tabbedFragments).forEach(f->trans.add(R.id.main_container,f));
        trans.commit();
        navView.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        setActiveFragment(floorplanFragment);
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_train:
                    setActiveFragment();
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
