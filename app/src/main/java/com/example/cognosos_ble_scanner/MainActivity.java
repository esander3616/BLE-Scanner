package com.example.cognosos_ble_scanner;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.cognosos_ble_scanner.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationHandler.LocationUpdateListener {

    private static final String TAG = "BLEScanner";
    private static final int PERMISSION_REQUEST_CODE = 2;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private LocationHandler locationHandler;
    private double longitude;
    private double latitude;
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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_CODE);
        } else {
            locationHandler = new LocationHandler(this, this);
        }

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
                util.createCSV(MainActivity.this, deviceListTextView.getText().toString());
            }
        });

        settingsButton.setOnClickListener(l -> {
            Intent settings = new Intent(this, Settings.class);
            settingsLauncher.launch(settings);
        });
    }

    public void onLocationUpdated(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        Log.d(TAG, "Updated coordinates: Lat=" + latitude + ", Lon=" + longitude);
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
            filter = Utils.getFilter(filterBox.getText().toString());
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
            bluetoothLeScanner.startScan(filter, Utils.makeSettings(), scanCallback);
        } else {
            bluetoothLeScanner.startScan(null, Utils.makeSettings(), scanCallback);  // Ensure settings are used
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (scanning) {
                    stopScanning();
                }
            }
        }, SCAN_TIME * 2 * 1000);
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
                    + ", RSSI: " + rssi + ", Address: " + address + ", Coordinate: (" + latitude + "," + longitude + ")" + "\n");
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
                        + ", RSSI: " + rssi + ", Address: " + address + ", Coordinate: (" + latitude + "," + longitude + ")" + "\n");
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
}