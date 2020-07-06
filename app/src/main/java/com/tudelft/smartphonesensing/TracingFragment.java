package com.tudelft.smartphonesensing;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.tudelft.smartphonesensing.util.DialogUtil;
import com.tudelft.smartphonesensing.util.RequirementsUtil;

import org.dpppt.android.sdk.BuildConfig;
import org.dpppt.android.sdk.DP3T;
import org.dpppt.android.sdk.DP3TCalibrationHelper;
import org.dpppt.android.sdk.InfectionStatus;
import org.dpppt.android.sdk.TracingStatus;
import org.dpppt.android.sdk.backend.ResponseCallback;
import org.dpppt.android.sdk.backend.models.ExposeeAuthMethodJson;
import org.dpppt.android.sdk.internal.AppConfigManager;
import org.dpppt.android.sdk.internal.BluetoothAdvertiseMode;
import org.dpppt.android.sdk.internal.BluetoothScanMode;
import org.dpppt.android.sdk.internal.BluetoothTxPowerLevel;
import org.dpppt.android.sdk.internal.database.Database;
import org.dpppt.android.sdk.util.FileUploadRepository;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TracingFragment extends Fragment {

    private static final String TAG = TracingFragment.class.getCanonicalName();

    private static final int REQUEST_CODE_PERMISSION_LOCATION = 1;
    private static final int REQUEST_CODE_SAVE_DB = 2;
    private static final int REQUEST_CODE_REPORT_EXPOSED = 3;

    private static final DateFormat DATE_FORMAT_SYNC = SimpleDateFormat.getDateTimeInstance();

    private static final String REGEX_VALIDITY_AUTH_CODE = "\\w+";
    private static final int EXPOSED_MIN_DATE_DIFF = -21;

    // This is part of the parameters fragment

    private static final int MIN_INTERVAL_SCANNING_SECONDS = 30;
    private static final int MAX_INTERVAL_SCANNING_SECONDS = 900;
    private static final int MIN_DURATION_SCANNING_SECONDS = 10;
    private Spinner spinnerScanMode;
    private Spinner spinnerUseScanResponse;
    private Spinner spinnerAdvertisingMode;
    private Spinner spinnerPowerLevel;
    private SeekBar seekBarScanInterval;
    private SeekBar seekBarScanDuration;
    private EditText inputScanInterval;
    private EditText inputScanDuration;

    private BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                checkPermissionRequirements();
                updateSdkStatus();
            }
        }
    };

    private BroadcastReceiver sdkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateSdkStatus();
        }
    };

    public static TracingFragment newInstance() {
        return new TracingFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tracing_fragment, container, false);


    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupUi(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        getContext().registerReceiver(bluetoothReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        getContext().registerReceiver(sdkReceiver, DP3T.getUpdateIntentFilter());
        checkPermissionRequirements();
        updateSdkStatus();

        AppConfigManager appConfigManager = AppConfigManager.getInstance(getContext());

        BluetoothScanMode scanMode = appConfigManager.getBluetoothScanMode();
        spinnerScanMode.setSelection(scanMode.ordinal());

        int interval = (int) (appConfigManager.getScanInterval() / 1000);
        seekBarScanInterval.setProgress(interval - MIN_INTERVAL_SCANNING_SECONDS);
        int duration = (int) (appConfigManager.getScanDuration() / 1000);
        seekBarScanDuration.setProgress(duration - MIN_DURATION_SCANNING_SECONDS);

        boolean useScanResponse = appConfigManager.isScanResponseEnabled();
        spinnerUseScanResponse.setSelection(useScanResponse ? 1 : 0);

        BluetoothAdvertiseMode selectedMode = appConfigManager.getBluetoothAdvertiseMode();
        spinnerAdvertisingMode.setSelection(selectedMode.ordinal());

        BluetoothTxPowerLevel selectedLevel = appConfigManager.getBluetoothTxPowerLevel();
        spinnerPowerLevel.setSelection(selectedLevel.ordinal());
    }

    @Override
    public void onPause() {
        super.onPause();
        getContext().unregisterReceiver(bluetoothReceiver);
        getContext().unregisterReceiver(sdkReceiver);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_SAVE_DB && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                OutputStream targetOut = getContext().getContentResolver().openOutputStream(uri);
                DP3TCalibrationHelper.exportDb(getContext(), targetOut, () ->
                        new Handler(getContext().getMainLooper()).post(() -> setExportDbLoadingViewVisible(false)));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return;
        } else if (requestCode == REQUEST_CODE_REPORT_EXPOSED) {
            if (resultCode == Activity.RESULT_OK) {
                long onsetDate = data.getLongExtra(ExposedDialogFragment.RESULT_EXTRA_DATE_MILLIS, -1);
                String authCodeBase64 = data.getStringExtra(ExposedDialogFragment.RESULT_EXTRA_AUTH_CODE_INPUT_BASE64);
                sendInfectedUpdate(getContext(), new Date(onsetDate), authCodeBase64);
            }
        }
    }

    private void setupUi(View view) {
        Button locationButton = view.findViewById(R.id.home_button_location);
        locationButton.setOnClickListener(
                v -> requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                        REQUEST_CODE_PERMISSION_LOCATION));

        Button batteryButton = view.findViewById(R.id.home_button_battery_optimization);
        batteryButton.setOnClickListener(
                v -> startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getContext().getPackageName()))));

        Button bluetoothButton = view.findViewById(R.id.home_button_bluetooth);
        bluetoothButton.setOnClickListener(v -> {
            if (BluetoothAdapter.getDefaultAdapter() != null) {
                BluetoothAdapter.getDefaultAdapter().enable();
            } else {
                Toast.makeText(getContext(), "No BluetoothAdapter found!", Toast.LENGTH_LONG).show();
            }
        });

        Button refreshButton = view.findViewById(R.id.home_button_sync);
        refreshButton.setOnClickListener(v -> resyncSdk());

        Button buttonStartAdvertising = view.findViewById(R.id.home_button_start_advertising);
        buttonStartAdvertising.setOnClickListener(v -> {
            DP3TCalibrationHelper.start(v.getContext(), true, false);
            updateSdkStatus();
        });

        Button buttonStartReceiving = view.findViewById(R.id.home_button_start_receiving);
        buttonStartReceiving.setOnClickListener(v -> {
            DP3TCalibrationHelper.start(v.getContext(), false, true);
            updateSdkStatus();
        });

        Button buttonClearData = view.findViewById(R.id.home_button_clear_data);
        buttonClearData.setOnClickListener(v -> {
            DialogUtil.showConfirmDialog(v.getContext(), R.string.dialog_clear_data_title,
                    (dialog, which) -> {
                        DP3T.clearData(v.getContext(), () ->
                                new Handler(getContext().getMainLooper()).post(this::updateSdkStatus));
                        MainActivity.initDP3T(v.getContext());
                    });
        });

        Button buttonSaveDb = view.findViewById(R.id.home_button_export_db);
        buttonSaveDb.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("application/sqlite");
            intent.putExtra(Intent.EXTRA_TITLE, "dp3t_sample_db.sqlite");
            startActivityForResult(intent, REQUEST_CODE_SAVE_DB);
            setExportDbLoadingViewVisible(true);
        });

        Button uploadDB = view.findViewById(R.id.home_button_upload_db);
        uploadDB.setOnClickListener(v -> {
            setUploadDbLoadingViewVisible(true);
            new FileUploadRepository()
                    .uploadDatabase(getContext(), AppConfigManager.getInstance(getContext()).getCalibrationTestDeviceName(),
                            new Callback<Void>() {
                                @Override
                                public void onResponse(Call<Void> call, Response<Void> response) {
                                    setUploadDbLoadingViewVisible(false);
                                }

                                @Override
                                public void onFailure(Call<Void> call, Throwable t) {
                                    t.printStackTrace();
                                    Toast.makeText(getContext(), "Upload failed!", Toast.LENGTH_LONG).show();
                                    setUploadDbLoadingViewVisible(false);
                                }
                            });
        });

        EditText deanonymizationDeviceId = view.findViewById(R.id.deanonymization_device_id);
        Switch deanonymizationSwitch = view.findViewById(R.id.deanonymization_switch);
        if (DP3TCalibrationHelper.getCalibrationTestDeviceName(getContext()) != null) {
            deanonymizationSwitch.setChecked(true);
            deanonymizationDeviceId.setText(DP3TCalibrationHelper.getCalibrationTestDeviceName(getContext()));
        }
        deanonymizationSwitch.setOnCheckedChangeListener((compoundButton, enabled) -> {
            if (enabled) {
                setDeviceId(deanonymizationDeviceId.getText().toString());
            } else {
                DP3TCalibrationHelper.disableCalibrationTestDeviceName(getContext());
            }
        });
        deanonymizationDeviceId.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (deanonymizationSwitch.isChecked()) {
                    setDeviceId(editable.toString());
                }
            }
        });

        seekBarScanInterval = view.findViewById(R.id.parameter_seekbar_scan_interval);
        inputScanInterval = view.findViewById(R.id.parameter_input_scan_interval);
        seekBarScanDuration = view.findViewById(R.id.parameter_seekbar_scan_duration);
        inputScanDuration = view.findViewById(R.id.parameter_input_scan_duration);

        spinnerScanMode = view.findViewById(R.id.parameter_spinner_scan_mode);
        ArrayAdapter<BluetoothScanMode> scanModeAdapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, BluetoothScanMode.values());
        spinnerScanMode.setAdapter(scanModeAdapter);
        spinnerScanMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setScanMode(BluetoothScanMode.values()[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        seekBarScanInterval.setMax(MAX_INTERVAL_SCANNING_SECONDS - MIN_INTERVAL_SCANNING_SECONDS);
        seekBarScanInterval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int intervalDuration = progress + MIN_INTERVAL_SCANNING_SECONDS;
                inputScanInterval.setText(String.valueOf(intervalDuration));
                int newMaxProgress = intervalDuration - 1 - MIN_DURATION_SCANNING_SECONDS;
                adjustNewDurationMaximum(newMaxProgress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setScanInterval(seekBar.getProgress() + MIN_INTERVAL_SCANNING_SECONDS);
            }
        });
        inputScanInterval.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String input = inputScanInterval.getText().toString();
                if (input.length() == 0) return true;
                try {
                    int inputIntervalSeconds = Integer.parseInt(input);
                    inputIntervalSeconds =
                            Math.min(MAX_INTERVAL_SCANNING_SECONDS, Math.max(MIN_INTERVAL_SCANNING_SECONDS, inputIntervalSeconds));
                    inputScanInterval.setText(String.valueOf(inputIntervalSeconds));
                    seekBarScanInterval.setProgress(inputIntervalSeconds - MIN_INTERVAL_SCANNING_SECONDS);
                    setScanInterval(inputIntervalSeconds);
                    hideKeyboard(v);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
                return true;
            }
            return false;
        });

        seekBarScanDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int scanDuration = progress + MIN_DURATION_SCANNING_SECONDS;
                inputScanDuration.setText(String.valueOf(scanDuration));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setScanDuration(seekBar.getProgress() + MIN_DURATION_SCANNING_SECONDS);
            }
        });
        inputScanDuration.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String input = inputScanDuration.getText().toString();
                if (input.length() == 0) return true;
                try {
                    int inputDurationSeconds = Integer.parseInt(input);
                    inputDurationSeconds =
                            Math.min(getScanInterval() - 1, Math.max(MIN_DURATION_SCANNING_SECONDS, inputDurationSeconds));
                    inputScanDuration.setText(String.valueOf(inputDurationSeconds));
                    seekBarScanDuration.setProgress(inputDurationSeconds - MIN_DURATION_SCANNING_SECONDS);
                    setScanDuration(inputDurationSeconds);
                    hideKeyboard(v);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
                return true;
            }
            return false;
        });

        spinnerUseScanResponse = view.findViewById(R.id.parameter_spinner_use_scan_response);
        ArrayAdapter<Boolean> useScanResponseAdapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, new Boolean[] { false, true });
        spinnerUseScanResponse.setAdapter(useScanResponseAdapter);
        spinnerUseScanResponse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setUseScanResponse(position == 1);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        spinnerAdvertisingMode = view.findViewById(R.id.parameter_spinner_advertising_mode);
        ArrayAdapter<BluetoothAdvertiseMode> advertisingModeAdapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, BluetoothAdvertiseMode.values());
        spinnerAdvertisingMode.setAdapter(advertisingModeAdapter);
        spinnerAdvertisingMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setAdvertisingMode(BluetoothAdvertiseMode.values()[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        spinnerPowerLevel = view.findViewById(R.id.parameter_spinner_power_level);
        ArrayAdapter<BluetoothTxPowerLevel> powerLevelAdapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, BluetoothTxPowerLevel.values());
        spinnerPowerLevel.setAdapter(powerLevelAdapter);
        spinnerPowerLevel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setAdvertPowerLevel(BluetoothTxPowerLevel.values()[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        TextView version_info = view.findViewById(R.id.version_info);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Zurich"));
        version_info.setText(
                org.dpppt.android.sdk.BuildConfig.VERSION_NAME + " / " + sdf.format(org.dpppt.android.sdk.BuildConfig.BUILD_TIME) + " / " + org.dpppt.android.sdk.BuildConfig.FLAVOR + " / " +
                        BuildConfig.BUILD_TYPE);
    }

    private void setDeviceId(String deviceId) {
        if (deviceId.length() > 4) {
            deviceId = deviceId.substring(0, 4);
        } else {
            while (deviceId.length() < 4) {
                deviceId = deviceId + " ";
            }
        }
        DP3TCalibrationHelper.setCalibrationTestDeviceName(getContext(), deviceId);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSION_LOCATION) {
            checkPermissionRequirements();
            updateSdkStatus();
        }
    }

    private void checkPermissionRequirements() {
        View view = getView();
        Context context = getContext();
        if (view == null || context == null) return;

        boolean locationGranted = RequirementsUtil.isLocationPermissionGranted(context);
        Button locationButton = view.findViewById(R.id.home_button_location);
        locationButton.setEnabled(!locationGranted);
        locationButton.setText(locationGranted ? R.string.req_location_permission_granted
                : R.string.req_location_permission_ungranted);

        boolean batteryOptDeactivated = RequirementsUtil.isBatteryOptimizationDeactivated(context);
        Button batteryButton = view.findViewById(R.id.home_button_battery_optimization);
        batteryButton.setEnabled(!batteryOptDeactivated);
        batteryButton.setText(batteryOptDeactivated ? R.string.req_battery_deactivated
                : R.string.req_battery_deactivated);

        boolean bluetoothActivated = RequirementsUtil.isBluetoothEnabled();
        Button bluetoothButton = view.findViewById(R.id.home_button_bluetooth);
        bluetoothButton.setEnabled(!bluetoothActivated);
        bluetoothButton.setText(bluetoothActivated ? R.string.req_bluetooth_active
                : R.string.req_bluetooth_inactive);
    }

    private void resyncSdk() {
        new Thread(() -> {
            DP3T.sync(getContext());
            new Handler(getContext().getMainLooper()).post(this::updateSdkStatus);
        }).start();
    }

    private void updateSdkStatus() {
        View view = getView();
        Context context = getContext();
        if (context == null || view == null) return;

        TracingStatus status = DP3T.getStatus(context);

        TextView statusText = view.findViewById(R.id.home_status_text);
        statusText.setText(formatStatusString(status));

        Button buttonStartStopTracking = view.findViewById(R.id.home_button_start_stop_tracking);
        boolean isRunning = status.isAdvertising() || status.isReceiving();
        buttonStartStopTracking.setSelected(isRunning);
        buttonStartStopTracking.setText(getString(isRunning ? R.string.button_tracking_stop
                : R.string.button_tracking_start));
        buttonStartStopTracking.setOnClickListener(v -> {
            if (isRunning) {
                DP3T.stop(v.getContext());
            } else {
                DP3T.start(v.getContext());
            }
            updateSdkStatus();
        });

        Button buttonStartAdvertising = view.findViewById(R.id.home_button_start_advertising);
        buttonStartAdvertising.setEnabled(!isRunning);
        Button buttonStartReceiving = view.findViewById(R.id.home_button_start_receiving);
        buttonStartReceiving.setEnabled(!isRunning);

        Button buttonClearData = view.findViewById(R.id.home_button_clear_data);
        buttonClearData.setEnabled(!isRunning);
        Button buttonSaveDb = view.findViewById(R.id.home_button_export_db);
        buttonSaveDb.setEnabled(!isRunning);
        Button buttonUploadDb = view.findViewById(R.id.home_button_upload_db);
        buttonUploadDb.setEnabled(!isRunning);

        Button buttonReportInfected = view.findViewById(R.id.home_button_report_infected);
        buttonReportInfected.setEnabled(status.getInfectionStatus() != InfectionStatus.INFECTED);
        buttonReportInfected.setText(R.string.button_report_infected);
        buttonReportInfected.setOnClickListener(
                v -> {
                    Calendar minCal = Calendar.getInstance();
                    minCal.add(Calendar.DAY_OF_YEAR, EXPOSED_MIN_DATE_DIFF);
                    DialogFragment exposedDialog =
                            ExposedDialogFragment.newInstance(minCal.getTimeInMillis(), REGEX_VALIDITY_AUTH_CODE);
                    exposedDialog.setTargetFragment(this, REQUEST_CODE_REPORT_EXPOSED);
                    exposedDialog.show(getParentFragmentManager(), ExposedDialogFragment.class.getCanonicalName());
                });

        EditText deanonymizationDeviceId = view.findViewById(R.id.deanonymization_device_id);
        Switch deanonymizationSwitch = view.findViewById(R.id.deanonymization_switch);
        if (DP3TCalibrationHelper.getCalibrationTestDeviceName(getContext()) != null) {
            deanonymizationSwitch.setChecked(true);
            deanonymizationDeviceId.setText(DP3TCalibrationHelper.getCalibrationTestDeviceName(getContext()));
        } else {
            deanonymizationSwitch.setChecked(false);
            deanonymizationDeviceId.setText("0000");
        }
    }

    private SpannableString formatStatusString(TracingStatus status) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        boolean isTracking = status.isAdvertising() || status.isReceiving();
        builder.append(getString(isTracking ? R.string.status_tracking_active : R.string.status_tracking_inactive)).append("\n")
                .setSpan(new StyleSpan(Typeface.BOLD), 0, builder.length() - 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        builder.append(getString(R.string.status_advertising, status.isAdvertising())).append("\n")
                .append(getString(R.string.status_receiving, status.isReceiving())).append("\n");

        long lastSyncDateUTC = status.getLastSyncDate();
        String lastSyncDateString =
                lastSyncDateUTC > 0 ? DATE_FORMAT_SYNC.format(new Date(lastSyncDateUTC)) : "n/a";
        builder.append(getString(R.string.status_last_synced, lastSyncDateString)).append("\n")
                .append(getString(R.string.status_self_infected, status.getInfectionStatus() == InfectionStatus.INFECTED))
                .append("\n")
                .append(getString(R.string.status_been_exposed, status.getInfectionStatus() == InfectionStatus.EXPOSED))
                .append("\n")
                .append(getString(R.string.status_number_contacts, status.getNumberOfContacts())).append("\n")
                .append(getString(R.string.status_number_handshakes, new Database(getContext()).getHandshakes().size()));

        Collection<TracingStatus.ErrorState> errors = status.getErrors();
        if (errors != null && errors.size() > 0) {
            int start = builder.length();
            builder.append("\n");
            for (TracingStatus.ErrorState error : errors) {
                builder.append("\n").append(error.toString());
            }
            builder.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.red, null)),
                    start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return new SpannableString(builder);
    }

    private void sendInfectedUpdate(Context context, Date onsetDate, String codeInputBase64) {
        setExposeLoadingViewVisible(true);

        DP3T.sendIAmInfected(context, onsetDate, new ExposeeAuthMethodJson(codeInputBase64), new ResponseCallback<Void>() {
            @Override
            public void onSuccess(Void response) {
                DialogUtil.showMessageDialog(context, getString(R.string.dialog_title_success),
                        getString(R.string.dialog_message_request_success));
                setExposeLoadingViewVisible(false);
                updateSdkStatus();
            }

            @Override
            public void onError(Throwable throwable) {
                DialogUtil.showMessageDialog(context, getString(R.string.dialog_title_error),
                        throwable.getLocalizedMessage());
                Log.e(TAG, throwable.getMessage(), throwable);
                setExposeLoadingViewVisible(false);
            }
        });
    }

    private void setExposeLoadingViewVisible(boolean visible) {
        View view = getView();
        if (view != null) {
            view.findViewById(R.id.home_loading_view_exposed).setVisibility(visible ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.home_button_report_infected).setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void setExportDbLoadingViewVisible(boolean visible) {
        View view = getView();
        if (view != null) {
            view.findViewById(R.id.home_loading_view_export_db).setVisibility(visible ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.home_button_export_db).setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void setUploadDbLoadingViewVisible(boolean visible) {
        View view = getView();
        if (view != null) {
            view.findViewById(R.id.home_loading_view_upload_db).setVisibility(visible ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.home_button_upload_db).setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
        }
    }

    // Parameters fragment

    private void setScanMode(BluetoothScanMode mode) {
        AppConfigManager.getInstance(getContext()).setBluetoothScanMode(mode);
    }

    private int getScanInterval() {
        return seekBarScanInterval.getProgress() + MIN_INTERVAL_SCANNING_SECONDS;
    }

    private void setScanInterval(int interval) {
        AppConfigManager.getInstance(getContext()).setScanInterval(interval * 1000);
    }

    private void setScanDuration(int duration) {
        AppConfigManager.getInstance(getContext()).setScanDuration(duration * 1000);
    }

    private void setAdvertPowerLevel(BluetoothTxPowerLevel powerLevel) {
        AppConfigManager.getInstance(getContext()).setBluetoothPowerLevel(powerLevel);
    }

    private void setAdvertisingMode(BluetoothAdvertiseMode mode) {
        AppConfigManager.getInstance(getContext()).setBluetoothAdvertiseMode(mode);
    }

    private void setUseScanResponse(boolean useScanResponse) {
        AppConfigManager.getInstance(getContext()).setUseScanResponse(useScanResponse);
    }

    private void hideKeyboard(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void adjustNewDurationMaximum(int durationProgressMaximum) {
        int currentDurationProgress = seekBarScanDuration.getProgress();
        seekBarScanDuration.setMax(durationProgressMaximum);
        if (currentDurationProgress > durationProgressMaximum) {
            setScanDuration(durationProgressMaximum + MIN_DURATION_SCANNING_SECONDS);
        }
    }
}
