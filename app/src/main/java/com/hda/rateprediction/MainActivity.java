package com.hda.rateprediction;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    TextView textTimeDisplay;
    TextView textRsrpDisplay;
    TextView textRsrqDisplay;
    TextView textRssiDisplay;
    TextView textRssnrDisplay;
    TextView textGpsDisplay;
    Button buttonStartResume, buttonStop, buttonSaveToCsv;
    ToggleButton toggleButtonRSRP, toggleButtonRSRQ, toggleButtonRSSI, toggleButtonRSSNR, toggleButtonGPS;
    Timestamp timestamp;

    ArrayList<String[]> networkDataList = new ArrayList<>();
    String[] networkData;
    int timeCount = 0;
    boolean isRunning = false, displayRSRP = true, displayRSRQ = true, displayRSSI = true, displayRSSNR = true, displayGPS = true;
    int level, rsrp, rsrq, rssi, rssnr, cqi, asuLevel, timingAdvance, dbm, bandwidth, ci, earFcn, tac, pci;
    double latitude, longitude;


    LineGraphSeries<DataPoint> rsrpLine = new LineGraphSeries<>(new DataPoint[]{new DataPoint(0, 0)});
    LineGraphSeries<DataPoint> rsrqLine = new LineGraphSeries<>(new DataPoint[]{new DataPoint(0, 0)});
    LineGraphSeries<DataPoint> rssiLine = new LineGraphSeries<>(new DataPoint[]{new DataPoint(0, 0)});
    LineGraphSeries<DataPoint> rssnrLine = new LineGraphSeries<>(new DataPoint[]{new DataPoint(0, 0)});
    LineGraphSeries<DataPoint> latitudeLine = new LineGraphSeries<>(new DataPoint[]{new DataPoint(0, 0)});
    LineGraphSeries<DataPoint> longitudeLine = new LineGraphSeries<>(new DataPoint[]{new DataPoint(0, 0)});

    static String TAG = "hda";
    private MyService timerService;
    int Brightness;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        registerReceiver(broadcastReceiver,new IntentFilter(MyService.str_receiver));

        try {
            Brightness = Settings.System.getInt(this.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        try {
            if (checkSystemWritePermission()) {
                Settings.System.putInt(this.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(this.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,1);
                Toast.makeText(this, "Brightness set succesfully ", Toast.LENGTH_SHORT).show();
            }else {
                Toast.makeText(this, "Allow modify system settings ==> ON ", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.i("ringtoon",e.toString());
            Toast.makeText(this, "unable to set Brightness ", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        textTimeDisplay = findViewById(R.id.timeDisplay);
        textRsrpDisplay = findViewById(R.id.rsrpDisplay);
        textRsrqDisplay = findViewById(R.id.rsrqDisplay);
        textRssiDisplay = findViewById(R.id.rssiDisplay);
        textRssnrDisplay = findViewById(R.id.rssnrDisplay);
        textGpsDisplay = findViewById(R.id.gpsDisplay);
        buttonStartResume = findViewById(R.id.startButton);
        buttonStop = findViewById(R.id.stopButton);
        buttonSaveToCsv = findViewById(R.id.saveButton);
        toggleButtonRSRP = findViewById(R.id.rsrpToggleButton);
        toggleButtonRSRQ = findViewById(R.id.rsrqToggleButton);
        toggleButtonRSSI = findViewById(R.id.rssiToggleButton);
        toggleButtonRSSNR = findViewById(R.id.rssnrToggleButton);
        toggleButtonGPS = findViewById(R.id.gpsToggleButton);
        final GraphView graph = findViewById(R.id.graph);

        initializeGraph(graph);
        requestPermission();

        buttonStartResume.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                    buttonStartResume.setText("Start");
                    Intent i = new Intent(MainActivity.this, MyService.class);
//                    try {
//                        startService(i);
//                    }catch ( Exception e1){
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                            this.startForegroundService(i);
//                        }else {
//                            //Crashlytics.log("crash for first time, trying another.");
//                            this.startService(i);
//                        }
//                    }
                    ContextCompat.startForegroundService(getApplicationContext(),i);
                    //startService(i);
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                buttonStartResume.setText("Resume");
                Intent i = new Intent(MainActivity.this, MyService.class);
                stopService(i);
            }
        });

        buttonSaveToCsv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeDataToCSV();
            }
        });

        toggleButtonRSRP.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                displayRSRP = isChecked;
            }
        });

        toggleButtonRSRQ.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                displayRSRQ = isChecked;
            }
        });

        toggleButtonRSSI.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                displayRSSI = isChecked;
            }
        });

        toggleButtonRSSNR.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                displayRSSNR = isChecked;
            }
        });

        toggleButtonGPS.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                displayGPS = isChecked;
            }
        });

    }

    private boolean checkSystemWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(Settings.System.canWrite(this))
                return true;
            else
                openAndroidPermissionsMenu();
        }
        return false;
    }

    private void openAndroidPermissionsMenu() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + this.getPackageName()));
            this.startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, " Permissions: granted");
            }
        }
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //String s = intent.getAction();
            String str_time = intent.getStringExtra("displaytime");
            timeCount = intent.getIntExtra("time", 0);
            rsrp = intent.getIntExtra("rsrp", 0);
            rsrq = intent.getIntExtra("rsrq", 0);
            rssi = intent.getIntExtra("rssi", 0);
            rssnr = intent.getIntExtra("rssnr", 0);
            level = intent.getIntExtra("level", 0);
            cqi = intent.getIntExtra("cqi", 0);
            asuLevel = intent.getIntExtra("asuLevel", 0);
            timingAdvance = intent.getIntExtra("timingAdvance", 0);
            dbm = intent.getIntExtra("dbm", 0);
            bandwidth = intent.getIntExtra("bandwidth", 0);
            ci = intent.getIntExtra("ci", 0);
            earFcn = intent.getIntExtra("earFcn", 0);
            tac = intent.getIntExtra("tac", 0);
            pci = intent.getIntExtra("pci", 0);
            latitude = intent.getDoubleExtra("latitude", 0.0);
            longitude = intent.getDoubleExtra("longitude", 0.0);
            networkData = intent.getStringArrayExtra("networkData");

            textTimeDisplay.setText(str_time);
            prepareCSVData();
            appendDataToGraph();
            displayDataToUser();
        }
    };

    private void prepareCSVData() {
        timestamp = new Timestamp(System.currentTimeMillis());
        networkData = new String[]{String.valueOf(timestamp), String.valueOf(rsrp), String.valueOf(rsrq), String.valueOf(rssi), String.valueOf(rssnr), String.valueOf(latitude), String.valueOf(longitude), String.valueOf(cqi), String.valueOf(asuLevel), String.valueOf(timingAdvance), String.valueOf(dbm), String.valueOf(bandwidth), String.valueOf(ci), String.valueOf(earFcn), String.valueOf(tac), String.valueOf(pci)};
        networkDataList.add(networkData);
    }

    private void displayDataToUser() {
        String rsrpFormat = String.format(Locale.getDefault(), "%d dBm", rsrp);
        String rsrqFormat = String.format(Locale.getDefault(), "%d dBm", rsrq);
        String rssiFormat = String.format(Locale.getDefault(), "%d dBm", rssi);
        String rssnrFormat = String.format(Locale.getDefault(), "%d dBm", rssnr);
        String gpsFormat = String.format(Locale.getDefault(), "%f,%f ", latitude, longitude);
        String defaultDisplay = "N/A";

        if (displayRSRP)
            textRsrpDisplay.setText(rsrpFormat);
        else
            textRsrpDisplay.setText(defaultDisplay);

        if (displayRSRQ)
            textRsrqDisplay.setText(rsrqFormat);
        else
            textRsrqDisplay.setText(defaultDisplay);

        if (displayRSSI)
            textRssiDisplay.setText(rssiFormat);
        else
            textRssiDisplay.setText(defaultDisplay);

        if (displayRSSNR)
            textRssnrDisplay.setText(rssnrFormat);
        else
            textRssnrDisplay.setText(defaultDisplay);

        if (displayGPS)
            textGpsDisplay.setText(gpsFormat);
        else
            textGpsDisplay.setText(defaultDisplay);

    }

    private void appendDataToGraph() {
        Log.i(TAG, "appendDataToGraph: " + timeCount + " " + rsrp + " " + rsrq + " " + rssi + " " + rssnr + " " + latitude + " " + longitude);
        try {
            if (displayRSRP) {
                rsrpLine.appendData(new DataPoint(timeCount, rsrp), true, 10000);
            } else {
                rsrpLine.appendData(new DataPoint(timeCount, 0), true, 10000);
            }
            if (displayRSRQ) {
                rsrqLine.appendData(new DataPoint(timeCount, rsrq), true, 10000);
            } else {
                rsrqLine.appendData(new DataPoint(timeCount, 0), true, 10000);
            }
            if (displayRSSI && rssi < 100) {
                rssiLine.appendData(new DataPoint(timeCount, rssi), true, 10000);
            } else {
                rssiLine.appendData(new DataPoint(timeCount, 0), true, 10000);
            }
            if (displayRSSNR && rssnr < 100) {
                rssnrLine.appendData(new DataPoint(timeCount, rssnr), true, 10000);
            } else {
                rssnrLine.appendData(new DataPoint(timeCount, 0), true, 10000);
            }
            if (displayGPS) {
                latitudeLine.appendData(new DataPoint(timeCount, latitude), true, 10000);
                longitudeLine.appendData(new DataPoint(timeCount, longitude), true, 10000);
            } else {
                latitudeLine.appendData(new DataPoint(timeCount, 0), true, 10000);
                longitudeLine.appendData(new DataPoint(timeCount, 0), true, 10000);
            }

        } catch (IllegalArgumentException e) {
            Log.i(TAG, "run: Exception adding data to graph " + e.getMessage());
        }
    }

    private void initializeGraph(GraphView graph) {
        try {
            rsrpLine.setColor(Color.RED);
            rsrpLine.setTitle("RSRP");
            rsrqLine.setColor(Color.BLUE);
            rsrqLine.setTitle("RSRQ");
            rssiLine.setColor(Color.GREEN);
            rssiLine.setTitle("RSSI");
            rssnrLine.setColor(Color.MAGENTA);
            rssnrLine.setTitle("RSSNR");
            latitudeLine.setTitle("LATITUDE");
            latitudeLine.setColor(Color.LTGRAY);
            longitudeLine.setTitle("LONGITUDE");
            longitudeLine.setColor(Color.DKGRAY);
            graph.setTitle("Network parameters and GPS coordinates");
            graph.getLegendRenderer().setVisible(true);
            graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);

            graph.addSeries(rsrpLine);
            graph.addSeries(rsrqLine);
            graph.addSeries(rssiLine);
            graph.addSeries(rssnrLine);
            graph.addSeries(latitudeLine);
            graph.addSeries(longitudeLine);

            graph.getViewport().setScrollable(true);
            graph.getViewport().setScrollableY(true);
            graph.getViewport().setScalable(true);
            graph.getViewport().setScalableY(true);


        } catch (IllegalArgumentException e) {
            Log.i(TAG, "initializeGraph: Exception" + e.getMessage());
        }
    }

    private void writeDataToCSV() {
        String folderPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        File folder = new File(folderPath);
        timestamp = new Timestamp(System.currentTimeMillis());
        //String fileName = "NetworkData.csv";
        String fileName = timestamp.toString() + ".csv";
        if (!folder.exists()) {
            if (!folder.mkdir()) {
                Toast.makeText(getApplicationContext(), "Folder cannot be created", Toast.LENGTH_LONG).show();
                Log.i(TAG, "writeDataToCSV: File " + folderPath + " cannot be created");
            }
        } else {
            String csv = folderPath + "/" + fileName;
            try {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                    Log.i(TAG, "Write Permissions not granted");
                    return;
                }

                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 3);
                    Log.i(TAG, "Read Permissions not granted");
                    return;
                }

                CSVWriter csvWriter = new CSVWriter(new FileWriter(csv, true));
                String[] headerNames = {"Systime", "RSRP(dBm)", "RSRQ(dB)", "RSSI(dBm)", "RSSNR", "LATITUDE", "LONGITUDE", "CQI", "ASULEVEL", "TIMINGADVANCE", "DBM", "BANDWIDTH", "CI", "EARFCN", "TAC", "PCI"};
                csvWriter.writeNext(headerNames);

                csvWriter.writeAll(networkDataList);
                csvWriter.close();
                Toast.makeText(getApplicationContext(), "Write data Successful to " + folderPath, Toast.LENGTH_LONG).show();
                Log.i(TAG, "writeDataToCSV: File " + folderPath + " Successful");

            } catch (IOException e) {
                e.printStackTrace();
                Log.i(TAG, "writeDataToCSV: Exception");
                Log.i(TAG, e.getMessage());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(broadcastReceiver,new IntentFilter(MyService.str_receiver));

    }

    @Override
    protected void onPause() {
        super.onPause();
        registerReceiver(broadcastReceiver,new IntentFilter(MyService.str_receiver));
        Settings.System.putInt(this.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(this.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,1);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(broadcastReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Settings.System.putInt(this.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(this.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,Brightness);
        super.onDestroy();
    }
}