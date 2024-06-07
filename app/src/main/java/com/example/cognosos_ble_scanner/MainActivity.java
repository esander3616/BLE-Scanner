package com.example.cognosos_ble_scanner;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.cognosos_ble_scanner.utils.Utils;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BLEScanner";
    private static final int PERMISSION_REQUEST_CODE = 2;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private Handler handler = new Handler();

    private TextView textView;
    private TextView deviceListTextView;
    private Button scanButton;
    private Button createCsvButton;
    private Button settingsButton;
    private EditText filterBox;
    private boolean scanning;
    private List<ScanFilter> filter;
    private Utils util = new Utils();
    private static int SCAN_TIME = 10;

    private ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK) {
                    Toast.makeText(this, "Bluetooth not enabled, exiting.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    private ActivityResultLauncher<Intent> settingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    SCAN_TIME = result.getData().getIntExtra("SCAN_TIME", 0);
                    Toast.makeText(this, "Scan time set to " + SCAN_TIME + " seconds", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);
        deviceListTextView = findViewById(R.id.deviceListTextView);
        scanButton = findViewById(R.id.scanButton);
        createCsvButton = findViewById(R.id.createCsvButton);
        filterBox = findViewById(R.id.filterBox);
        settingsButton = findViewById(R.id.settingsButton);

        // Check if BLE is supported on the device
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE is not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Bluetooth adapter
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(enableBtIntent);
        }

        // Request necessary permissions
        requestPermissions();

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (allPermissionsGranted()) {
                    initializeFilterAndStartScanning();
                    scanButton.setVisibility(View.GONE);
                } else {
                    requestPermissions();
                }
            }
        });

        createCsvButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createCSV();
            }
        });

        settingsButton.setOnClickListener(l -> {
            Intent settings = new Intent(this, Settings.class);
            settingsLauncher.launch(settings);
        });
    }

    private boolean allPermissionsGranted() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        // && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && allPermissionsGranted()) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied, exiting.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void initializeFilterAndStartScanning() {
        try {
            filter = util.getFilter(filterBox.getText().toString());
        } catch (Exception e) {
            filter = new ArrayList<>();
        }
        displayFilters();
        startScanning();
    }

    private void displayFilters() {
        textView.setText("Scanning with filters on: \n");
        if (filter != null && !filter.isEmpty()) {
            for (ScanFilter filt : filter) {
                textView.append(filt.getDeviceAddress() + "\n");
            }
        } else {
            textView.append("No filters applied\n");
        }
    }

    private void startScanning() {
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "Bluetooth LE Scanner not available", Toast.LENGTH_SHORT).show();
            return;
        }

        scanning = true;
        deviceListTextView.setText("");  // Clear previous scan results

        if (filter != null && !filter.isEmpty()) {
            bluetoothLeScanner.startScan(filter, util.makeSettings(), scanCallback);
        } else {
            bluetoothLeScanner.startScan(null, util.makeSettings(), scanCallback);  // Ensure settings are used
        }

        // Stop scanning after 10 seconds
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (scanning) {
                    stopScanning();
                }
            }
        }, SCAN_TIME * 1000);
    }

    private void stopScanning() {
        if (scanning && bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(scanCallback);
            scanning = false;
            Toast.makeText(this, "Scan complete", Toast.LENGTH_SHORT).show();
            scanButton.setVisibility(View.VISIBLE);
        }
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, @NonNull ScanResult result) {
            BluetoothDevice device = result.getDevice();
            int rssi = result.getRssi();
            String address = device.getAddress();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            Log.d(TAG, "Device found: " + device.getName() + ", RSSI: " + rssi + ", Address: " + address);
            updateUI("Time: " + timestamp + ", Device: " + (device.getName() != null ? device.getName() : "Unknown")
                    + ", RSSI: " + rssi + ", Address: " + address + "\n");
        }

        @Override
        public void onBatchScanResults(@NonNull List<ScanResult> results) {
            for (ScanResult result : results) {
                BluetoothDevice device = result.getDevice();
                int rssi = result.getRssi();
                String address = device.getAddress();
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                Log.d(TAG, "Device found: " + device.getName() + ", RSSI: " + rssi + ", Address: " + address);
                updateUI("Time: " + timestamp + ", Device: " + (device.getName() != null ? device.getName() : "Unknown")
                        + ", RSSI: " + rssi + ", Address: " + address + "\n");
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan failed with error code: " + errorCode);
            Toast.makeText(MainActivity.this, "Scan failed with error code: "
                    + errorCode, Toast.LENGTH_SHORT).show();
            scanning = false;
            scanButton.setVisibility(View.VISIBLE);  // Ensure the button is visible again
        }
    };

    private void updateUI(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                deviceListTextView.append(message);
            }
        });
    }

    private void createCSV() {
        OutputStream outputStream = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "BLE_ScanResults.csv");
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/BLE_Scans");

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), contentValues);
                if (uri != null) {
                    outputStream = getContentResolver().openOutputStream(uri);
                }
            } else {
                String baseDir = Environment.getExternalStorageDirectory().getAbsolutePath();
                String fileName = "BLE_ScanResults.csv";
                String filePath = baseDir + "/" + fileName;
                outputStream = new FileOutputStream(filePath);
            }

            if (outputStream != null) {
                OutputStreamWriter writer = new OutputStreamWriter(outputStream);
                writer.append("Time, Device Name, RSSI, Address\n");

                String[] devices = deviceListTextView.getText().toString().split("\n");
                for (String device : devices) {
                    String[] deviceDetails = device.split(", ");
                    if (deviceDetails.length == 4) {
                        String time = deviceDetails[0].split(": ")[1];
                        String deviceName = deviceDetails[1].split(": ").length > 1 ? deviceDetails[1].split(": ")[1] : "Unknown";
                        String rssi = deviceDetails[2].split(": ")[1];
                        String address = deviceDetails[3].split(": ")[1];

                        writer.append(time).append(",");
                        writer.append(deviceName).append(",");
                        writer.append(rssi).append(",");
                        writer.append(address).append("\n");
                    } else {
                        Log.e(TAG, "Incorrectly formatted device string: " + device);
                    }
                }

                writer.flush();
                writer.close();
                Toast.makeText(this, "CSV created successfully", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to create CSV", e);
            Toast.makeText(this, "Failed to create CSV", Toast.LENGTH_SHORT).show();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close OutputStream", e);
                }
            }
        }
    }
}