package com.tudelft.smartphonesensing;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class MainActivity extends AppCompatActivity {

    final ManageFragment manageFragment = new ManageFragment();
    final TestFragment predictFragment = new TestFragment();
    final FloorplanFragment floorplanFragment = new FloorplanFragment();
    final CellFragment cellFragment = new CellFragment();
    final FragmentManager fm = getSupportFragmentManager();
    final List<Fragment> tabbedFragments = Arrays.asList(manageFragment, predictFragment, floorplanFragment);
    final List<Fragment> allFragments = Arrays.asList(manageFragment, predictFragment, floorplanFragment, cellFragment);

    Stack<Fragment> fragmentStack = new Stack<>();
    Fragment activeFragment = null;

    public void setActiveFragment(Fragment active, boolean editStack) {
        if (editStack) {
            if (tabbedFragments.indexOf(active) != -1) {
                fragmentStack.clear();
            } else {
                fragmentStack.push(activeFragment);
            }
        }

        FragmentTransaction trans = fm.beginTransaction();
        allFragments.forEach(trans::hide);
        trans.show(active);
        trans.commit();
        activeFragment = active;
        backcallback.setEnabled(fragmentStack.size() > 0);
    }

    OnBackPressedCallback backcallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (fragmentStack.size() > 0) {
                setActiveFragment(fragmentStack.pop(), false);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView navView = findViewById(R.id.bottomNavigationView);
        FragmentTransaction trans = fm.beginTransaction();
        allFragments.forEach(f -> trans.add(R.id.main_container, f));
        trans.commit();
        setActiveFragment(floorplanFragment, true);

        navView.setOnNavigationItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.navigation_manage:
                    setActiveFragment(manageFragment, true);
                    return true;
                case R.id.navigation_floorplan:
                    setActiveFragment(floorplanFragment, true);
                    return true;
                case R.id.navigation_predict:
                    setActiveFragment(predictFragment, true);
                    return true;
            }
            return false;
        });

        // This callback will only be called when MyFragment is at least Started.
        getOnBackPressedDispatcher().addCallback(this, backcallback);
    }
}
