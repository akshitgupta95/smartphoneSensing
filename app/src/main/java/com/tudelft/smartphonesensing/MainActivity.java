package com.tudelft.smartphonesensing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.tudelft.smartphonesensing.util.NotificationUtil;
import com.tudelft.smartphonesensing.util.PreferencesUtil;

import org.dpppt.android.sdk.DP3T;
import org.dpppt.android.sdk.backend.models.ApplicationInfo;
import org.dpppt.android.sdk.internal.util.ProcessUtil;
import org.dpppt.android.sdk.util.SignatureUtil;

import java.security.PublicKey;

import static org.dpppt.android.sdk.DP3T.setMatchingParameters;

public class MainActivity extends AppCompatActivity {

    final Fragment manageFragment = new ManageFragment();
    final Fragment testFragment = new TestFragment();
    final Fragment floorplanFragment = new FloorplanFragment();
    final Fragment tracingFragment = new TracingFragment();
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
        fm.beginTransaction().add(R.id.main_container, tracingFragment, "4").hide(tracingFragment).commit();
        navView.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        if (ProcessUtil.isMainProcess(this)) {
            registerReceiver(sdkReceiver, DP3T.getUpdateIntentFilter());
            initDP3T(this);
        }
    }

    public static void initDP3T(Context context) {
        PublicKey publicKey = SignatureUtil.getPublicKeyFromBase64OrThrow("LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0NCk1Ga3dFd1lIS29aSXpqMENBUVlJS29aSXpqMERBUWNEUWdBRTUxb0ZVUTVCTXlXdGRuUEkwUHVZc3QyNXE2dXQNCklRMTU3Yy9uYXN1TkozbEN2T0lFU0lDZFhwT1FUUUdWNisxWDh1WStOWmZ5WlFTR090R3hxTFdOcmc9PQ0KLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0tDQo=");
        String url = "https://app-covid-4254.herokuapp.com/";
        DP3T.init(context, new ApplicationInfo("org.dpppt.demo", url, url), publicKey);

//        CertificatePinner certificatePinner = new CertificatePinner.Builder()
//                .add("https://app-covid-4254.herokuapp.com/", "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=")
//                .build();
//        DP3T.setCertificatePinner(certificatePinner);

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
                    fm.beginTransaction().hide(tracingFragment).commit();
                    fm.beginTransaction().hide(getActiveFragment()).show(manageFragment).commit();
                    active = manageFragment;
                    return true;

                case R.id.navigation_floorplan:
                    Log.v("Fragment", getActiveFragment().toString());
                    fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    fm.beginTransaction().hide(testFragment).commit();
                    fm.beginTransaction().hide(manageFragment).commit();
                    fm.beginTransaction().hide(tracingFragment).commit();
                    fm.beginTransaction().hide(getActiveFragment()).show(floorplanFragment).commit();
                    active = floorplanFragment;
                    return true;

                case R.id.navigation_test:
                    Log.v("Fragment", getActiveFragment().toString());
                    fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    fm.beginTransaction().hide(floorplanFragment).commit();
                    fm.beginTransaction().hide(manageFragment).commit();
                    fm.beginTransaction().hide(tracingFragment).commit();
                    fm.beginTransaction().hide(getActiveFragment()).show(testFragment).commit();
                    active = testFragment;
                    return true;

                case R.id.navigation_tracing:
                    Log.v("Fragment", getActiveFragment().toString());
                    fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    fm.beginTransaction().hide(floorplanFragment).commit();
                    fm.beginTransaction().hide(manageFragment).commit();
                    fm.beginTransaction().hide(testFragment).commit();
                    fm.beginTransaction().hide(getActiveFragment()).show(tracingFragment).commit();
                    active = tracingFragment;
                    return true;
            }
            return false;
        }
    };
}
