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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
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

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    private EditText filterBox;
    private boolean scanning;
    private List<ScanFilter> filter;
    private Utils util = new Utils();

    private ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Bluetooth is enabled, but don't start scanning automatically
                } else {
                    Toast.makeText(this, "Bluetooth not enabled, exiting.", Toast.LENGTH_SHORT).show();
                    finish();
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
    }

    private boolean allPermissionsGranted() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
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
        }, 10000);
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
            Log.d(TAG, "Device found: " + device.getName() + ", RSSI: " + rssi + ", Address: " + address);
            updateUI("Device: " + (device.getName() != null ? device.getName() : "Unknown") + ", RSSI: " + rssi + ", Address: " + address + "\n");
        }

        @Override
        public void onBatchScanResults(@NonNull List<ScanResult> results) {
            for (ScanResult result : results) {
                BluetoothDevice device = result.getDevice();
                int rssi = result.getRssi();
                String address = device.getAddress();
                Log.d(TAG, "Device found: " + device.getName() + ", RSSI: " + rssi + ", Address: " + address);
                updateUI("Device: " + (device.getName() != null ? device.getName() : "Unknown") + ", RSSI: " + rssi + ", Address: " + address + "\n");
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan failed with error code: " + errorCode);
            Toast.makeText(MainActivity.this, "Scan failed with error code: " + errorCode, Toast.LENGTH_SHORT).show();
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
        try {
            FileWriter writer = new FileWriter(getExternalFilesDir(null) + "/scanned_devices.csv");
            writer.append("Device Name, RSSI, Address\n");
            String[] devices = deviceListTextView.getText().toString().split("\n");
            for (String device : devices) {
                String[] deviceDetails = device.split(", ");
                if (deviceDetails.length == 3) {
                    String deviceName = deviceDetails[0].split(": ").length > 1 ? deviceDetails[0].split(": ")[1] : "Unknown";
                    String rssi = deviceDetails[1].split(": ")[1];
                    String address = deviceDetails[2].split(": ")[1];
                    writer.append(deviceName).append(",");
                    writer.append(rssi).append(",");
                    writer.append(address).append("\n");
                } else {
                    Log.e(TAG, "Incorrectly formatted device string: " + device);
                }
            }
            writer.flush();
            writer.close();
            Toast.makeText(this, "CSV file created successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error creating CSV file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}