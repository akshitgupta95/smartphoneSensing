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

import org.jetbrains.annotations.NotNull;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.ArrayList;
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
        accelerometer = sensorMan.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        rotationSensor = sensorMan.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        rawAccelerometer = sensorMan.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        stepCounter = sensorMan.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        //sensorMan.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorMan.registerListener(this, rawAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorMan.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        //sensorMan.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void free() {
        sensorMan.unregisterListener(this);
    }


    private Quaternion lastRotation = Quaternion.fromIncompleteUnit(0, 0, 0);

    private float[] state = new float[1 + 3 + 3 + 3 + 1];

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
        }
        String str = "";
        for (int i = 0; i < state.length; i++) {
            str += (i == 0 ? "" : ",") + String.format(Locale.US, "%.6f", state[i]);
        }
        socket.send(str);
    }

    private double lastStepTime = 0;
    private final double stepCooldown = 0.3;
    private final double characteristicWindowtime = 5.0;
    private AccelMeasurement lastMeasurement = null;
    private Vec3 avgMagnitude = new Vec3(0, 0, 0);
    private double stepSizeMeters = 0.4;

    private void newMeasurement(SensorEvent event) {
        AccelMeasurement measurement = new AccelMeasurement(event.values, event.timestamp, lastRotation);

        Log.v("ACC", String.format("%5.1f,%5.1f,%5.1f", measurement.accelWorld.x, measurement.accelWorld.y, measurement.accelWorld.z));
        if (lastMeasurement == null) {
            avgMagnitude = measurement.accelWorld;
        } else {
            double mag = measurement.accelPhone.getMagnitude();
            double timestep = measurement.time - lastMeasurement.time;
            double p = Math.exp(-timestep / characteristicWindowtime);
            avgMagnitude = avgMagnitude.multiply(p).add(measurement.accelWorld.multiply(1 - p));

            Vec3 diff = measurement.accelWorld.add(avgMagnitude.multiply(-1));
            if (diff.getMagnitude() > 5 && measurement.time > lastStepTime + stepCooldown) {
                lastStepTime = measurement.time;
                //Vec3 dir = avgMagnitude;
                Vec3 dir = measurement.orientation.conjugate().permute(new Vec3(0, 1, 0));
                double hormag = Math.sqrt(dir.x * dir.x + dir.y * dir.y);
                onmove.accept(dir.x / hormag * stepSizeMeters, dir.y / hormag * stepSizeMeters);
            }
        }
        lastMeasurement = measurement;

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
