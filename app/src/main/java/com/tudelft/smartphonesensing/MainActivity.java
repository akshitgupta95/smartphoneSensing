package com.tudelft.smartphonesensing;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

public class MainActivity extends AppCompatActivity {
    //TODO do this by passing arguments instead
    static ModelState modelState = new ModelState();

    BottomNavigationView navView;
    final ManageFragment manageFragment = new ManageFragment();
    final TestFragment predictFragment = new TestFragment();
    final FloorplanFragment floorplanFragment = new FloorplanFragment();
    final CellFragment cellFragment = new CellFragment();
    final FragmentManager fm = getSupportFragmentManager();
    final List<Fragment> tabbedFragments = Arrays.asList(manageFragment, predictFragment, floorplanFragment);
    final List<Fragment> allFragments = Arrays.asList(manageFragment, predictFragment, floorplanFragment, cellFragment);
    final HashMap<Integer, Fragment> menumap = new HashMap<>();

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

        //TODO is there a better way to reverse lookup a key/value pair?
        int activemanu = menumap.keySet().stream().filter(k -> menumap.get(k) == active).findFirst().orElse(-1);
        navView.setSelectedItemId(activemanu);
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

        modelState.setContext(this);

        AppDatabase db = AppDatabase.getInstance(this);
        //get the last saved floor data or generate a default one
        FloorplanDataDAO.FloorplanData floordata = db.floorplanDataDAO().getLastSaved();
        if (floordata != null) {
            modelState.loadFloor(floordata.getId());
        }else{
            modelState.loadNewDefaultFloor("Default");
        }


        menumap.put(R.id.navigation_manage, manageFragment);
        menumap.put(R.id.navigation_floorplan, floorplanFragment);
        menumap.put(R.id.navigation_predict, predictFragment);

        setContentView(R.layout.activity_main);
        navView = findViewById(R.id.bottomNavigationView);
        FragmentTransaction trans = fm.beginTransaction();
        allFragments.forEach(f -> trans.add(R.id.main_container, f));
        trans.commit();
        setActiveFragment(floorplanFragment, true);

        navView.setOnNavigationItemSelectedListener(view -> {
            Fragment newfrag = menumap.get(view.getItemId());
            if (newfrag == null || activeFragment == newfrag) {
                return false;
            }
            setActiveFragment(newfrag, true);
            return true;
        });

        // This callback will only be called when MyFragment is at least Started.
        getOnBackPressedDispatcher().addCallback(this, backcallback);
    }
}
