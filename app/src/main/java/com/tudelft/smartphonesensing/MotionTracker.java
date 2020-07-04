package com.tudelft.smartphonesensing;

import android.content.Context;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.renderscript.Matrix4f;
import android.util.FloatMath;
import android.util.Log;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;
import org.jtransforms.dct.DoubleDCT_1D;
import org.jtransforms.fft.DoubleFFT_1D;
import org.locationtech.jts.math.Vector3D;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static android.content.Context.SENSOR_SERVICE;

public class MotionTracker implements SensorEventListener {
    BiConsumer<Double, Double> onmove;
    SensorManager sensorMan;
    OkHttpClient client;
    WebSocket socket;
    Sensor accelerometer;
    Sensor rotationSensor;
    Sensor rawAccelerometer;
    Sensor stepCounter;

    public MotionTracker(Context ctx, BiConsumer<Double, Double> onmove) {
        this.onmove = onmove;

        client = new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build();
        socket = client.newWebSocket(new Request.Builder().url("ws://192.168.178.20:8060/log").build(), new WebSocketListener() {
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response res) {
                Log.v("SOCK", t.getMessage());
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                super.onMessage(webSocket, text);
            }

            @Override
            public void onOpen(WebSocket sock, Response response) {
                Log.v("SOCK", "socket opened");
            }
        });

        sensorMan = (SensorManager) ctx.getSystemService(SENSOR_SERVICE);
        //accelerometer = sensorMan.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        rotationSensor = sensorMan.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        rawAccelerometer = sensorMan.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        stepCounter = sensorMan.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        //sensorMan.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorMan.registerListener(this, rawAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorMan.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorMan.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public double get2dNorthAngle() {
        //3d vector pointing to the y axis (long side) of the phone
        Vec3 vec = lastRotation.conjugate().permute(new Vec3(0, 1, 0));
        //get rotation of the x-y component (parallel to earth)
        double angle = Math.atan2(vec.y, vec.x) - Math.PI / 2;

        return angle;
    }

    public void free() {
        sensorMan.unregisterListener(this);
    }


    private Quaternion lastRotation = new Quaternion(1, 0, 0, 0);

    private double[] state = new double[1 + 3 + 3 + 3 + 1];

    @Override
    public void onSensorChanged(SensorEvent event) {
        state[0] = (float) (event.timestamp / 1e9);
        if (event.sensor == accelerometer) {
            state[1] = event.values[0];
            state[2] = event.values[1];
            state[3] = event.values[2];
        }
        if (event.sensor == rawAccelerometer) {
            state[4] = event.values[0];
            state[5] = event.values[1];
            state[6] = event.values[2];
            newMeasurement(event);
        }
        if (event.sensor == rotationSensor) {
            lastRotation = Quaternion.fromIncompleteUnit(event.values[0], event.values[1], event.values[2]);
            state[7] = event.values[0];
            state[8] = event.values[1];
            state[9] = event.values[2];
        }
        if (event.sensor == stepCounter) {
            state[10] = (float) (event.timestamp / 1e9);
            Vec3 dir = lastRotation.conjugate().permute(new Vec3(0, 1, 0));
            double hormag = Math.sqrt(dir.x * dir.x + dir.y * dir.y);
            //onmove.accept(dir.x / hormag * stepSizeMeters, dir.y / hormag * stepSizeMeters);
        }
    }

    private void sendState() {
        String str = "";
        for (int i = 0; i < state.length; i++) {
            str += (i == 0 ? "" : ",") + String.format(Locale.US, "%.6f", state[i]);
        }
        socket.send(str);
    }

    private double lastStepTime = 0;
    private final double stepCooldown = 0.3;
    private double stepSizeMeters = 0.4;

    private final double characteristicWindowtime = 3.0;
    private AccelMeasurement lastMeasurement = null;
    private final int windowSize = 70;
    private List<AccelMeasurement> history = new LinkedList<>();
    private Vec3 avgMagnitude = new Vec3(0, 0, 0);
    private double walkvelocity = 4f / 3.6;//4km/hr->m/s
    private double sampleInterval = 0.02;


    private void detectStepFourier() {
        double[] fftdata = new double[windowSize * 2];
        int i = 0;
        for (AccelMeasurement mdata : history) {
            fftdata[i * 2] = mdata.accelWorld.z;
            fftdata[i * 2 + 1] = 0;
            i++;
        }
        DoubleFFT_1D fft = new DoubleFFT_1D(windowSize);
        fft.complexForward(fftdata);

        //70 samples at 50hz->1.4sec window
        //[8-11] range=> [8-11]/1.4sec hz sec=[5.7-7.8]hz
        //kinda looks like the natural frequency of my arm instead of the frequency of walking, but seems more reliable
        //the value of 20 is experimental
        double maxabs = 0;
        for (int j = 8; j <= 11; j++) {
            double abs = Math.sqrt(fftdata[j * 2] * fftdata[j * 2] + fftdata[j * 2 + 1] * fftdata[j * 2 + 1]);
            maxabs = Math.max(maxabs, abs);
        }
        if (maxabs > 20) {
            //the fourier introduces some lag, get the direction of the middle of the window
            AccelMeasurement middlemeasure = history.get(history.size() - windowSize / 2);
            double dist = walkvelocity / 50;
            triggerStep(dist, middlemeasure);
        }
    }

    private void triggerStep(double dist, AccelMeasurement dirmeasure) {
        Vec3 dir = dirmeasure.orientation.conjugate().permute(new Vec3(0, 1, 0));
        double hormag = Math.sqrt(dir.x * dir.x + dir.y * dir.y);
        onmove.accept(dir.x / hormag * dist, dir.y / hormag * dist);
    }

    private double peaktopeak(List<AccelMeasurement> hist, int start, int end) {
        double submin = Double.POSITIVE_INFINITY;
        double submax = Double.NEGATIVE_INFINITY;
        for (int i = start; i < end; i++) {
            double accel = history.get(i).accelWorld.z;
            submin = Math.min(submin, accel);
            submax = Math.max(submax, accel);
        }
        return submax - submin;
    }

    private void detectStepHandcraft2() {
        final double highwindow = 0.12;
        final double lowwindow = 0.18;
        int highsamples = (int) Math.round(highwindow / sampleInterval);
        int lowsamples = (int) Math.round(lowwindow / sampleInterval);

        int index = history.size();

        double highp2p = peaktopeak(history, index - highsamples, index);
        index -= highsamples;
        double lowp2p = peaktopeak(history, index - lowsamples, index);

        final double multiplier = 2.0;
        final double mincap = 10.5;
        final double maxcap = 3.2;
        AccelMeasurement accel = history.get(history.size() - 1);
        if (highp2p > multiplier * lowp2p && lowp2p < mincap && highp2p > maxcap && lastStepTime + stepCooldown < accel.time) {
            triggerStep(stepSizeMeters, accel);
            lastStepTime = accel.time;
        }
        if (lowp2p < highp2p) {
            Log.v("ACCEL", String.format(Locale.US, "%.2f, %.2f", lowp2p, highp2p));
        }
    }

    private void detectStepHandcraft() {
        final double windowtime = 0.4;
        final double subwindowtime = 0.15;

        int numsamples = (int) Math.round(windowtime / sampleInterval);
        int numsubsamples = (int) Math.round(subwindowtime / sampleInterval);

        double avgsum = 0;
        for (int i = 0; i < history.size(); i++) {
            avgsum += history.get(i).accelWorld.z;
        }
        double windowavg = avgsum / history.size();

        double sum = 0;
        double minp2p = Double.POSITIVE_INFINITY;
        double maxp2p = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < numsamples - numsubsamples; i++) {
            double submin = Double.POSITIVE_INFINITY;
            double submax = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < numsubsamples; j++) {
                int histindex = history.size() - 1 - i - j;
                double accel = history.get(histindex).accelWorld.z;
                submin = Math.min(submin, accel);
                submax = Math.max(submax, accel);
            }
            double p2p = submax - submin;
            minp2p = Math.min(minp2p, p2p);
            maxp2p = Math.max(maxp2p, p2p);
        }

        Log.v("ACCEL", String.format(Locale.US, "%.2f, %.2f", minp2p, maxp2p));
        final double multiplier = 2.0;
        final double mincap = 1.5;
        final double maxcap = 4.0;
        AccelMeasurement accel = history.get(history.size() - 1);
        if (maxp2p > multiplier * minp2p && minp2p < mincap && maxp2p > maxcap && lastStepTime + stepCooldown < accel.time) {
            triggerStep(stepSizeMeters, accel);
            lastStepTime = accel.time;
        }
    }

    private void detectStepAvg() {
        final double avgwindowsec = 1.2;
        final double smoothwindowsec = 0.3;
        final double stepTreshold = 1.0;


        int windowsize = (int) Math.round(avgwindowsec / sampleInterval);
        double runningsum = 0;

        double[] avg = new double[history.size()];

        for (int i = 0; i < history.size(); i++) {
            AccelMeasurement front = history.get(i);
            runningsum += front.accelWorld.z;

            if (i - windowsize > 0) {
                AccelMeasurement back = history.get(i - windowsize);
                runningsum -= back.accelWorld.z;
            }
            avg[i] = runningsum / windowsize;
        }

        double backdiff = avg[avg.length - 2] - avg[avg.length - 3];
        double frontdiff = avg[avg.length - 1] - avg[avg.length - 2];
        AccelMeasurement last = history.get(history.size() - 1);
        if (backdiff > 0 && frontdiff < 0 && avg[avg.length - 1] > stepTreshold && lastStepTime + stepCooldown < last.time) {
            lastStepTime = last.time;
            triggerStep(stepSizeMeters, last);
        }
    }

    private void newMeasurement(SensorEvent event) {
        AccelMeasurement measurement = new AccelMeasurement(event.values, event.timestamp, lastRotation);
        history.add(measurement);
        while (history.size() > windowSize) {
            history.remove(0);
        }

        if (history.size() == windowSize) {
            //detectStepFourier();
            //detectStepAvg();
            //detectStepHandcraft();
            detectStepHandcraft2();
        }

        /*
        Log.v("ACC", String.format("%5.1f,%5.1f,%5.1f", measurement.accelWorld.x, measurement.accelWorld.y, measurement.accelWorld.z));
        if (lastMeasurement == null) {
            avgMagnitude = measurement.accelWorld;
        } else {
            double mag = measurement.accelPhone.getMagnitude();
            double timestep = measurement.time - lastMeasurement.time;
            double p = Math.exp(-timestep / characteristicWindowtime);
            avgMagnitude = avgMagnitude.multiply(p).add(measurement.accelWorld.multiply(1 - p));

            DoubleFFT_1D fft = new DoubleFFT_1D(70);
            fft.complexForward();

            Vec3 diff = measurement.accelWorld.add(avgMagnitude.multiply(-1));

            state[0] = measurement.time;
            state[1] = diff.x;
            state[2] = diff.y;
            state[3] = diff.z;
            sendState();
            if (diff.z > 2.3 && measurement.time > lastStepTime + stepCooldown) {
                lastStepTime = measurement.time;
                //Vec3 dir = avgMagnitude;
                Vec3 dir = measurement.orientation.conjugate().permute(new Vec3(0, 1, 0));
                double hormag = Math.sqrt(dir.x * dir.x + dir.y * dir.y);
                onmove.accept(dir.x / hormag * stepSizeMeters, dir.y / hormag * stepSizeMeters);
            }
        }
        lastMeasurement = measurement;
        */
    }

    static class AccelMeasurement {
        final Vec3 accelPhone;
        final Vec3 accelWorld;
        final Quaternion orientation;
        final double time;//seconds since phone startup

        AccelMeasurement(float[] raw, long time, Quaternion orientation) {
            accelPhone = new Vec3(raw[0], raw[1], raw[2]);
            this.orientation = orientation;
            //not 100% sure anymore about proper use of order of rotations here, but this seems correct
            accelWorld = orientation.conjugate().permute(accelPhone);
            this.time = time / 1e9d;
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }


    static class Vec3 {
        final double x;
        final double y;
        final double z;

        Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vec3 multiply(double v) {
            return new Vec3(v * x, v * y, v * z);
        }

        Vec3 add(Vec3 rhs) {
            return new Vec3(x + rhs.x, y + rhs.y, z + rhs.z);
        }

        Quaternion toQuaternion() {
            return Quaternion.fromPure(x, y, z);
        }

        double getMagnitude() {
            return Math.sqrt(x * x + y * y + z + z);
        }
    }

    static class Quaternion {
        final double x;
        final double y;
        final double z;
        final double w;

        Quaternion(double w, double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }

        Quaternion conjugate() {
            return new Quaternion(w, -x, -y, -z);
        }

        Vec3 permute(Vec3 target) {
            Quaternion r = permute(target.toQuaternion());
            return new Vec3(r.x, r.y, r.z);
        }

        Quaternion permute(Quaternion target) {
            return conjugate().multiply(target).multiply(this);
        }

        Quaternion multiply(Quaternion rhs) {
            double rw = w * rhs.w - x * rhs.x - y * rhs.y - z * rhs.z;
            double rx = x * rhs.w + w * rhs.x - z * rhs.y + y * rhs.z;
            double ry = y * rhs.w + z * rhs.x + w * rhs.y - x * rhs.z;
            double rz = z * rhs.w - y * rhs.x + x * rhs.y + w * rhs.z;
            return new Quaternion(rw, rx, ry, rz);
        }

        static Quaternion fromIncompleteUnit(double x, double y, double z) {
            double w = Math.sqrt(1d - Math.sqrt(x * x + y * y + z * z));
            return new Quaternion(w, x, y, z);
        }

        static Quaternion fromPure(double x, double y, double z) {
            return new Quaternion(0, x, y, z);
        }
    }
}
