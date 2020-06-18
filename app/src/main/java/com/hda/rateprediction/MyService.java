package com.hda.rateprediction;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.hda.rateprediction.App.CHANNEL_ID;


public class MyService extends Service {

    public static String str_receiver = "com.hda.rateprediction.receiver";
    private static final String TAG = "MyService";
    Intent intent;

    TelephonyManager tm = null;
    GPSTracker gpsTracker = new GPSTracker(MyService.this);
    int timeCount=0;
    int level, rsrp, rsrq, rssi, rssnr, cqi, asuLevel, timingAdvance, dbm, bandwidth, ci, earFcn, tac, pci;
    double latitude, longitude;
    Timestamp timestamp;
    ArrayList<String[]> networkDataList = new ArrayList<>();
    String[] networkData;

    boolean isFirstOperatorDisplay = true;
    boolean isRunning = false;
    String timeFormat;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.v(TAG, "onCreateService:");
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand:");
        Toast.makeText(getApplicationContext(), "Service started running in Background", Toast.LENGTH_SHORT).show();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Rate Prediction App")
                .setContentText("App is running......")
                .setSmallIcon(R.drawable.ic_android_)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1,notification);
        isRunning = true;
        startRecording();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        Log.d(TAG, "onDestroy");
        stopForeground(true);
        stopSelf();
        Toast.makeText(getApplicationContext(), "Service stopped running in Background",
                Toast.LENGTH_SHORT).show();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void startRecording(){
        //Toast.makeText(getApplicationContext(), "Start Recording", Toast.LENGTH_SHORT).show();
        timeCalculator();
        getNetworkParameters();
        getLocationData();
        //prepareCSVData();
        intent = new Intent(str_receiver);
        intent.putExtra("displaytime", timeFormat);
        intent.putExtra("time", timeCount);
        intent.putExtra("rsrp", rsrp);
        intent.putExtra("rsrq", rsrq);
        intent.putExtra("rssi", rssi);
        intent.putExtra("rssnr", rssnr);
        intent.putExtra("level", level);
        intent.putExtra("cqi", cqi);
        intent.putExtra("asuLevel", asuLevel);
        intent.putExtra("timingAdvance", timingAdvance);
        intent.putExtra("dbm", dbm);
        intent.putExtra("bandwidth", bandwidth);
        intent.putExtra("ci", ci);
        intent.putExtra("earFcn", earFcn);
        intent.putExtra("tac", tac);
        intent.putExtra("pci", pci);
        intent.putExtra("latitude", latitude);
        intent.putExtra("longitude", longitude);
        intent.putExtra("longitude", longitude);
        intent.putExtra("networkData", networkData);
        //Bundle bundle = new Bundle();
        //bundle.putParcelableArrayList("networkData",
        //        networkDataList);
        //intent.putExtras(bundle);
        sendBroadcast(intent);
        if(isRunning){
            delayHandler(1000);
        }
    }

    private void timeCalculator() {
        timeCount++;
        int minutes = timeCount / 60;
        int seconds = timeCount % 60;
        timeFormat = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        //Toast.makeText(getApplicationContext(), timeFormat, Toast.LENGTH_SHORT).show();
    }

    private void delayHandler(int milliseconds) {
        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.Q)
            @Override
            public void run() {
                startRecording();
            }
        };
        handler.postDelayed(runnable, milliseconds);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void getNetworkParameters() {
        String log = "";
        //Toast.makeText(getApplicationContext(), "Data is being recorded in background",Toast.LENGTH_SHORT).show();

        tm = (TelephonyManager) getSystemService(MyService.this.TELEPHONY_SERVICE);
        String networkOperator = tm.getNetworkOperatorName();
        Log.i(TAG, networkOperator);

        try {
            if (ActivityCompat.checkSelfPermission(MyService.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Location Permissions not granted");
                return;
            }

            if (tm.getAllCellInfo() == null) {
                Log.i(TAG, "getAllCellInfo returned null");
            } else {
                List<CellInfo> data = tm.getAllCellInfo();
                Log.i(TAG, data.toString());
                for (final CellInfo infodata : data) {
                    if (infodata instanceof CellInfoLte) {

                        final CellSignalStrengthLte lte = ((CellInfoLte) infodata).getCellSignalStrength();
                        level = lte.getLevel(); //Retrieve an abstract level value for the overall signal quality
                        rsrp = lte.getRsrp();  //Get reference signal received power in dBm
                        rsrq = lte.getRsrq();  //Get reference signal received quality
                        rssi = lte.getRssi();  //Get Received Signal Strength Indication (RSSI) in dBm The value range is [-113, -51] inclusively or CellInfo#UNAVAILABLE if unavailable.
                        rssnr = lte.getRssnr(); //Get reference signal signal-to-noise ratio
                        cqi = lte.getCqi();
                        asuLevel = lte.getAsuLevel();
                        timingAdvance = lte.getTimingAdvance();
                        dbm = lte.getDbm();
                        final CellIdentityLte lteCellInfo = ((CellInfoLte) infodata).getCellIdentity();
                        bandwidth = lteCellInfo.getBandwidth();
                        ci = lteCellInfo.getCi();
                        earFcn = lteCellInfo.getEarfcn();
                        tac = lteCellInfo.getTac();
                        pci = lteCellInfo.getPci();

                        log = " Operator: " + networkOperator + "\n Signal Quality: " + level + " dBm" + "\n RSRP: " + rsrp + " dBm" + "\n RSRQ: " + rsrq + " dBm" + "\n RSSI: " + rssi + " dBm" + "\n RSSNR: " + rssnr + "\n CQI: " + cqi + "\n ASU Level: " + asuLevel + "\n Timing Advance: " + timingAdvance + "\n dBm: " + dbm + "\n Bandwidth: " + bandwidth + "\n CI: " + ci + "\n Earfcn: " + earFcn + "\n TAC :" + tac + "\n PCI: " + pci;
                        Log.i(TAG, log);

                        if (isFirstOperatorDisplay) {
                            Toast.makeText(MyService.this, " Operator: " + networkOperator, Toast.LENGTH_LONG).show();
                            isFirstOperatorDisplay = false;
                        }

                    } else if (infodata instanceof CellInfoGsm) {
                        Log.i(TAG, "GSM Network Type");
                        notifyNetworkToUser("GSM");
                    } else if (infodata instanceof CellInfoCdma) {
                        Log.i(TAG, "CDMA Network Type");
                        notifyNetworkToUser("CDMA");
                    } else if (infodata instanceof CellInfoWcdma) {
                        Log.i(TAG, "WCDMA Network Type");
                        notifyNetworkToUser("WCDMA");
                    } else {
                        Log.i(TAG, "Unknown Network Type");
                        notifyNetworkToUser("Unknown");
                    }
                }
            }
        } catch (SecurityException e) {
            Log.i(TAG, "Exception");
            e.printStackTrace();
            Log.i(TAG, e.getMessage());
        }
    }

    private void notifyNetworkToUser(String info) {
        Toast.makeText(getApplicationContext(), "Warning! " + info + " network detected", Toast.LENGTH_SHORT).show();
    }

    private void getLocationData() {
        Location location = gpsTracker.getLocation();
        if (location != null) {
            latitude = location.getLatitude();
            longitude = location.getLongitude();
        } else {
            Log.i(TAG, "getLocationData: Location data not retrieved from device");
        }
        Log.i(TAG, "getLocationData: Latitude: " + latitude + " Longitude: " + longitude);
    }
}

