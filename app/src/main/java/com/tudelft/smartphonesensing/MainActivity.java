package com.tudelft.smartphonesensing;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.dpppt.android.sdk.DP3T;
import org.dpppt.android.sdk.util.SignatureUtil;

import java.security.PublicKey;

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

        initDP3T(this);
        DP3T.start(this);

    }

    public static void initDP3T(Context context) {
        PublicKey publicKey = SignatureUtil.getPublicKeyFromBase64OrThrow(
                "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUZrd0V3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0R" +
                        "RZ0FFWTc3MFZEWjJlZjZCYjh0UXZYWVJpcUFaemtHLwpwNWs0U3pTV3FRY00zNzlqTVN6c3JOaU5nc0" +
                        "hWZlRPeGFqMUFzQ3RrNmJVUDV1cDc3RU5nckVzVkh3PT0KLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0t");
        DP3T.init(context, "com.tudelft.smartphonesensing", true, publicKey);

//        CertificatePinner certificatePinner = new CertificatePinner.Builder()
//                .add("demo.dpppt.org", "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=")
//                .build();
//        DP3T.setCertificatePinner(certificatePinner);
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
