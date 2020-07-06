package com.tudelft.smartphonesensing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.tudelft.smartphonesensing.util.NotificationUtil;
import com.tudelft.smartphonesensing.util.PreferencesUtil;

import org.dpppt.android.sdk.DP3T;
import org.dpppt.android.sdk.backend.models.ApplicationInfo;
import org.dpppt.android.sdk.internal.util.ProcessUtil;
import org.dpppt.android.sdk.util.SignatureUtil;

import java.security.PublicKey;

import static org.dpppt.android.sdk.DP3T.setMatchingParameters;

import org.json.JSONException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

public class MainActivity extends AppCompatActivity {
    //TODO do this by passing arguments instead
    static ModelState modelState = new ModelState();
    //TODO YUK! remove!!! only used for debugging step counter atm
    static Context context;

    BottomNavigationView navView;
    final ManageFragment manageFragment = new ManageFragment();
    final TestFragment predictFragment = new TestFragment();
    final FloorplanFragment floorplanFragment = new FloorplanFragment();
    final CellFragment cellFragment = new CellFragment();
    final Fragment tracingFragment = new TracingFragment();
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

        //TODO remove both of these
        modelState.setContext(this);
        context=this;

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
        menumap.put(R.id.naviagation_tracing, tracingFragment);

        setContentView(R.layout.activity_main);
        navView = findViewById(R.id.bottomNavigationView);
        FragmentTransaction trans = fm.beginTransaction();
        allFragments.forEach(f -> trans.add(R.id.main_container, f));
        trans.commit();
        setActiveFragment(floorplanFragment, true);
          
        // INIT DP3T
        if (ProcessUtil.isMainProcess(this)) {
            registerReceiver(sdkReceiver, DP3T.getUpdateIntentFilter());
            initDP3T(this);
        }

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
      
    public static void initDP3T(Context context) {
        PublicKey publicKey = SignatureUtil.getPublicKeyFromBase64OrThrow("LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0NCk1Ga3dFd1lIS29aSXpqMENBUVlJS29aSXpqMERBUWNEUWdBRTUxb0ZVUTVCTXlXdGRuUEkwUHVZc3QyNXE2dXQNCklRMTU3Yy9uYXN1TkozbEN2T0lFU0lDZFhwT1FUUUdWNisxWDh1WStOWmZ5WlFTR090R3hxTFdOcmc9PQ0KLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0tDQo=");
        String url = "https://app-covid-4254.herokuapp.com/";
        DP3T.init(context, new ApplicationInfo("org.dpppt.demo", url, url), publicKey);

        setMatchingParameters(context, 73.0f, 1);
    }

    @Override
    public void onDestroy() {
        if (ProcessUtil.isMainProcess(this)) {
            unregisterReceiver(sdkReceiver);
        }
        super.onDestroy();
    }


    private BroadcastReceiver sdkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DP3T.getStatus(context).getExposureDays().size() > 0 && !PreferencesUtil.isExposedNotificationShown(context)) {
                NotificationUtil.showNotification(context, R.string.push_exposed_title,
                        R.string.push_exposed_text, R.drawable.ic_handshakes);
                PreferencesUtil.setExposedNotificationShown(context);
            }
        }
    };

}
