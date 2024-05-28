package com.example.ble_scanner.ui.device_display;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;

public class DeviceDisplayViewModel extends ViewModel {
    private BluetoothManager bleMan;
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private Handler handler = new Handler();
    private boolean scanning = false;
    private static final long SCAN_PERIOD = 10000; // 10 seconds
    private MutableLiveData<ScanResult> scanResults = new MutableLiveData<>();
    private MutableLiveData<String> scanStatus = new MutableLiveData<>();

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            scanResults.postValue(result);
        }
    };

    public BluetoothAdapter getAdapter(Context context) {
        this.bleMan = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.adapter = bleMan.getAdapter();
        return this.adapter;
    }

    public void scan_ble(BluetoothLeScanner scanner, Context context) {
        if (!scanning) {
            scanStatus.postValue("Scanning...");
            handler.postDelayed(() -> {
                scanning = false;
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                scanner.stopScan(leScanCallback);
                scanStatus.postValue("Scan complete.");
            }, SCAN_PERIOD);

            scanning = true;
            scanner.startScan(leScanCallback);
        } else {
            scanning = false;
            scanner.stopScan(leScanCallback);
            scanStatus.postValue("Scan stopped.");
        }
    }

    public MutableLiveData<ScanResult> getScanResults() {
        return scanResults;
    }

    public MutableLiveData<String> getScanStatus() {
        return scanStatus;
    }
}