package com.uod.uodiotapp;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
import com.robinhood.spark.SparkView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends Activity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private TextView sensorTextView, azureTextView;
    SparkView sparkView;
    private boolean isSensorEnabled = false;
    //ArrayList<Float> yDataArray = new ArrayList<>();
    Queue<Float> yQueue = new LinkedList<Float>();

    private Handler httpHandler = new Handler();
    boolean isHttpHandler = false;

    @Override
    public final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorTextView = findViewById(R.id.sensorTextView);
        azureTextView = findViewById(R.id.azureTextView);

        Button startButton = findViewById(R.id.startButton);
        Button stopButton = findViewById(R.id.stopButton);

        startButton.setEnabled(true);
        stopButton.setEnabled(false);

        sparkView = (SparkView) findViewById(R.id.sparkview);

        // Get an instance of the sensor service, and use that to get an instance of
        // a particular sensor.
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if(lightSensor == null){
            sensorTextView.setText("Sensor.TYPE_LIGHT not Available");
        } else {
            sensorTextView.setText("Sensor.TYPE_LIGHT Available");
        }


        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSensorEnabled = true;
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                stop();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSensorEnabled = false;
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                start();
            }
        });

    }
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            try {
                if(yQueue.size() > 0){
                    float illuminance = yQueue.poll();
                    iotInjection(illuminance);
                }
                else {
                    azureTextView.setText("Azure IOT (Sync Complete)");
                    stop();
                }
            } catch (IOException e) {
                azureTextView.setText("Azure IOT (Sync Complete)");
            }
            if(isHttpHandler) {
                start();
            }
        }
    };

    public void stop() {
        isHttpHandler = false;
        httpHandler.removeCallbacks(runnable);
    }

    public void start() {
        isHttpHandler = true;
        httpHandler.postDelayed(runnable, 5000);
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        if(isSensorEnabled){
            float illuminance = event.values[0];
            yQueue.add(illuminance);
            double[] doubleArray = yQueue.stream()
                    .mapToDouble(f -> f != null ? f : Float.NaN)
                    .toArray();
            sparkView.setAdapter(new IotSparkAdapter(doubleArray));
            sensorTextView.setText("Illuminance: " + illuminance);

            try {
                azureTextView.setText("Azure IOT: " + doubleArray.length);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void iotInjection(float illuminance) throws IOException {
        String iotInjectionFunctionAppUrl = "https://uod-iot-injection-function-app.azurewebsites.net/api/Illuminance?data=" + illuminance + "&code=hlFiLZQYuBpfxOWSP4F9QW-Ez463rNP7R28YacmuwELmAzFu1v1jdQ==";
        RequestQueue queue = Volley.newRequestQueue(this);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, iotInjectionFunctionAppUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        azureTextView.setText("Azure IOT: " + yQueue.size() + " (Sync 5s)");
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                azureTextView.setText("Azure IOT: " + yQueue.size() + " (Sync Error)");
                stop();
            }
        });
        queue.add(stringRequest);
    }

    @Override
    protected void onResume() {
        // Register a listener for the sensor.
        super.onResume();
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        // Be sure to unregister the sensor when the activity pauses.
        super.onPause();
        sensorManager.unregisterListener(this);
    }

}