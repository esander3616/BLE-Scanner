package com.example.cognosos_ble_scanner.utils;

import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class Utils extends AppCompatActivity {
    private final String TAG = "BLE_Scanner";
    public static List<ScanFilter> getFilter(String addrs) {
        if (addrs == null || addrs.isEmpty()) {
            return new ArrayList<>();
        }
        List<ScanFilter> ret = new ArrayList<>();
        String[] pieces = addrs.split(",");
        for (String piece : pieces) {
            try {
                ScanFilter filt = new ScanFilter.Builder()
                        .setDeviceAddress(piece.trim().toUpperCase())
                        .build();
                ret.add(filt);
            } catch (IllegalArgumentException e) {}
        }
        return ret;
    }

    public static ScanSettings makeSettings() {
        return new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
    }

    public void createCSV(Context context, String deviceList) {
        new Thread(() -> {
            OutputStream outputStream = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "BLE_ScanResults.csv");
                    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/BLE_Scans");

                    Uri uri = context.getContentResolver().insert(MediaStore.Files.getContentUri("external"), contentValues);
                    if (uri != null) {
                        outputStream = context.getContentResolver().openOutputStream(uri);
                    }
                } else {
                    String baseDir = Environment.getExternalStorageDirectory().getAbsolutePath();
                    String fileName = "BLE_ScanResults.csv";
                    String filePath = baseDir + "/" + fileName;
                    outputStream = new FileOutputStream(filePath);
                }
                if (outputStream != null) {
                    OutputStreamWriter writer = new OutputStreamWriter(outputStream);
                    writer.append("Time, Device Name, RSSI, Address, Coordinate \n");
                    String[] devices = deviceList.split("\n");
                    for (String device : devices) {
                        String[] deviceDetails = device.split(", ");
                        if (deviceDetails.length == 5) {
                            String time = deviceDetails[0].split(": ")[1];
                            String deviceName = deviceDetails[1].split(": ").length > 1 ? deviceDetails[1].split(": ")[1] : "Unknown";
                            String rssi = deviceDetails[2].split(": ")[1];
                            String address = deviceDetails[3].split(": ")[1];
                            String location = deviceDetails[4].split(": ")[1];

                            writer.append(time).append(",");
                            writer.append(deviceName).append(",");
                            writer.append(rssi).append(",");
                            writer.append(address).append(",");
                            writer.append(location).append("\n");
                        }
                    }
                    writer.flush();
                    writer.close();
                    runOnUiThread(() -> Toast.makeText(context, "CSV created successfully", Toast.LENGTH_SHORT).show());
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to create CSV", e);
                runOnUiThread(() -> Toast.makeText(context, "Failed to create CSV", Toast.LENGTH_SHORT).show());
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to close OutputStream", e);
                    }
                }
            }
        }).start();
    }
}