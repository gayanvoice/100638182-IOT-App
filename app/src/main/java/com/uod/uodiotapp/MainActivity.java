package com.uod.uodiotapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.robinhood.spark.SparkView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;

public class MainActivity extends Activity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor cameraSensor;
    private TextView cameraTextView, batteryTemperatureTextView, batteryMiscTextView, azureIotCameraTextView, azureIotBatteryTextView;
    SparkView sparkViewIlluminance, sparkViewTemperature;
    private boolean isSensorEnabled = false;
    Queue<Double> illuminanceQueue = new LinkedList<Double>();
    Queue<Double> temperatureQueue = new LinkedList<Double>();
    Queue<Integer> voltageQueue = new LinkedList<Integer>();
    Queue<Boolean> isChargingQueue = new LinkedList<Boolean>();
    Queue<String> powerQueue = new LinkedList<String>();

    private Handler cameraHandler = new Handler();
    private Handler batteryHandler = new Handler();
    boolean isCameraHttpHandler = false;
    boolean isBatteryHttpHandler = false;


    @Override
    public final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraTextView = findViewById(R.id.cameraTextView);
        batteryTemperatureTextView = findViewById(R.id.batteryTemperatureTextView);
        batteryMiscTextView = findViewById(R.id.batteryMiscTextView);

        azureIotCameraTextView = findViewById(R.id.azureIotCameraTextView);
        azureIotBatteryTextView = findViewById(R.id.azureIotBatteryTextView);

        Button startButton = findViewById(R.id.startButton);
        Button stopButton = findViewById(R.id.stopButton);

        startButton.setEnabled(true);
        stopButton.setEnabled(false);

        sparkViewIlluminance = (SparkView) findViewById(R.id.sparkViewIlluminance);
        sparkViewTemperature = (SparkView) findViewById(R.id.sparkViewTemperature);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        cameraSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if(cameraSensor == null){
            cameraTextView.setText("Sensor.TYPE_LIGHT not Available");
        } else {
            cameraTextView.setText("Sensor.TYPE_LIGHT Available");
        }
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSensorEnabled = true;
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                stopCameraHttpHandler();
                stopBatteryHttpHandler();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSensorEnabled = false;
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                startCameraHttpHandler();
                startBatteryHttpHandler();
            }
        });

    }

    private final Runnable cameraRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if(illuminanceQueue.size() > 0){
                    double illuminance = illuminanceQueue.poll();
                    iotCameraInjection(illuminance);
                }
                else {
                    azureIotCameraTextView.setText("Azure IOT Camera (Sync Complete)");
                    stopCameraHttpHandler();
                }
            } catch (IOException e) {
                azureIotCameraTextView.setText("Azure IOT Camera (Sync Error)" + e);
                stopCameraHttpHandler();
            }
            if(isCameraHttpHandler) {
                startCameraHttpHandler();
            }
        }
    };
    private final Runnable batteryRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if(temperatureQueue.size() > 0 && voltageQueue.size() > 0 && isChargingQueue.size() > 0 && powerQueue.size() > 0){
                    double temperature = temperatureQueue.poll();
                    int voltage = voltageQueue.poll();
                    boolean isCharging = isChargingQueue.poll();
                    String power = powerQueue.poll();
                    iotBatteryInjection(temperature, voltage, isCharging, power);
                }
                else {
                    azureIotBatteryTextView.setText("Azure IOT Battery (Sync Complete)");
                    stopBatteryHttpHandler();
                }
            } catch (IOException e) {
                azureIotBatteryTextView.setText("Azure IOT Battery (Sync Error)" + e);
                stopBatteryHttpHandler();
            }
            if(isBatteryHttpHandler) {
                startBatteryHttpHandler();
            }
        }
    };
    public void stopCameraHttpHandler() {
        isCameraHttpHandler = false;
        cameraHandler.removeCallbacks(cameraRunnable);
    }

    public void startCameraHttpHandler() {
        isCameraHttpHandler = true;
        cameraHandler.postDelayed(cameraRunnable, 5000);
    }
    public void stopBatteryHttpHandler() {
        isBatteryHttpHandler = false;
        batteryHandler.removeCallbacks(batteryRunnable);
    }

    public void startBatteryHttpHandler() {
        isBatteryHttpHandler = true;
        batteryHandler.postDelayed(batteryRunnable, 5000);
    }
    private void getBatteryTemperature() {
        BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                temperatureQueue.add(((intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0))/10.0));
            }
        };
        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryLevelReceiver, batteryLevelFilter);
    }
    private void getBatteryVoltage() {
        BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                voltageQueue.add(intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0));
            }
        };
        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryLevelReceiver, batteryLevelFilter);
    }
    private void getBatteryIsCharging() {
        BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                if(intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_CHARGING) {
                    isChargingQueue.add(true);
                }
                else {
                    isChargingQueue.add(false);
                }

            }
        };
        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryLevelReceiver, batteryLevelFilter);
    }
    private void getBatteryPower() {
        BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                if(chargePlug == 2) {
                    powerQueue.add("USB");
                }
                else {
                    powerQueue.add("BATTERY");
                }

            }
        };
        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryLevelReceiver, batteryLevelFilter);
    }
    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        if(isSensorEnabled){
            double illuminance = event.values[0];
            double temperature = 0.0;
            int voltage = 0;
            boolean isCharging = false;
            String power = null;

            getBatteryTemperature();
            getBatteryVoltage();
            getBatteryIsCharging();
            getBatteryPower();

            illuminanceQueue.add(illuminance);

            double[] illuminanceArray = illuminanceQueue.stream()
                    .mapToDouble(d -> d != null ? d : Double.NaN)
                    .toArray();
            double[] temperatureArray = temperatureQueue.stream()
                    .mapToDouble(d -> d != null ? d : Double.NaN)
                    .toArray();
            int[] voltageArray = voltageQueue.stream()
                    .mapToInt(i -> i != null ? i : 0)
                    .toArray();

            Object [] isChargingArray = isChargingQueue.toArray();
            Object [] powerArray = powerQueue.toArray();

            sparkViewIlluminance.setAdapter(new IotSparkAdapter(illuminanceArray));
            sparkViewTemperature.setAdapter(new IotSparkAdapter(temperatureArray));

            if(temperatureArray.length > 0){
                temperature = temperatureArray[temperatureArray.length - 1];
            }

            if(voltageArray.length > 0){
                voltage = voltageArray[voltageArray.length - 1];
            }

            if(isChargingArray.length > 0){
                isCharging = (boolean) isChargingArray[isChargingArray.length - 1];
            }

            if(powerArray.length > 0){
                power = (String) powerArray[powerArray.length - 1];
            }

            Gson gson = new Gson();

            CameraTelemetryDataModel cameraTelemetryDataModel = new CameraTelemetryDataModel();
            cameraTelemetryDataModel.setIlluminance(illuminance);

            BatteryTelemetryDataModel batteryTelemetryDataModel = new BatteryTelemetryDataModel();
            batteryTelemetryDataModel.setTemperature(temperature);
            batteryTelemetryDataModel.setVoltage(voltage);
            batteryTelemetryDataModel.setIsCharging(isCharging);
            batteryTelemetryDataModel.setPower(power);



            cameraTextView.setText("Camera " + gson.toJson(cameraTelemetryDataModel));
            batteryTemperatureTextView.setText("Battery {\"temperature\":" + gson.toJson(batteryTelemetryDataModel.getTemperature()) + "}");
            batteryMiscTextView.setText("Battery " + gson.toJson(batteryTelemetryDataModel));

            try {
                azureIotCameraTextView.setText("Azure IOT Camera: " + illuminanceArray.length);
                azureIotBatteryTextView.setText("Azure IOT Battery: C[" + isChargingArray.length + "] P[" + powerArray.length + "] T[" + temperatureArray.length + "] V[" + voltageArray.length + "]");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void iotCameraInjection(double illuminance) throws IOException {
        String iotInjectionFunctionAppUrl = "https://100638182-iot-ingestion-camera-function-app.azurewebsites.net/api/Light?Illuminance=" + illuminance
                + "&code=8qL_rHSHV7eihZ8fr3HXDx5WlsNzY_I9zl5Pu8QBAP8MAzFu1x2hfQ==";
        RequestQueue queue = Volley.newRequestQueue(this);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, iotInjectionFunctionAppUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        azureIotCameraTextView.setText("Azure IOT Camera: Illuminance" + illuminanceQueue.size() + " (Sync 5s)");
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                azureIotCameraTextView.setText("Azure IOT Camera: Illuminance " + illuminanceQueue.size() + " (Sync Error)");
                stopCameraHttpHandler();
            }
        });
        queue.add(stringRequest);
    }
    private void iotBatteryInjection(double temperature, int voltage, boolean isCharging, String power) throws IOException {
        //https://100638182-iot-ingestion-battery-function-app.azurewebsites.net/api/Info?code=0aUXylr-X1wVigs_xIAdUB3tuw7xgtkeXVukxPd6kN9kAzFuWepSPQ==
        String iotInjectionFunctionAppUrl = "https://100638182-iot-ingestion-battery-function-app.azurewebsites.net/api/Info?Temperature=" + temperature +
                "&Voltage=" + voltage + "&IsCharging=" + isCharging + "&Power=" + power + "&code=0aUXylr-X1wVigs_xIAdUB3tuw7xgtkeXVukxPd6kN9kAzFuWepSPQ==";
        RequestQueue queue = Volley.newRequestQueue(this);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, iotInjectionFunctionAppUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        azureIotBatteryTextView.setText("Azure IOT Battery: C[" + isChargingQueue.size() + "] P[" + powerQueue.size() + "] T[" + temperatureQueue.size() + "] V[" + voltageQueue.size() + "] (Sync 5s)");
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                azureIotBatteryTextView.setText("Azure IOT Battery: C[" + isChargingQueue.size() + "] P[" + powerQueue.size() + "] T[" + temperatureQueue.size() + "] V[" + voltageQueue.size() + "] (Sync Error)");
                stopBatteryHttpHandler();
            }
        });
        queue.add(stringRequest);
    }
    @Override
    protected void onResume() {
        // Register a listener for the sensor.
        super.onResume();
        sensorManager.registerListener(this, cameraSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        // Be sure to unregister the sensor when the activity pauses.
        super.onPause();
        sensorManager.unregisterListener(this);
    }


}