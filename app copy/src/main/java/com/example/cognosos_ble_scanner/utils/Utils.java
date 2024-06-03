package com.example.cognosos_ble_scanner.utils;

import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;

import java.util.ArrayList;
import java.util.List;

public class Utils {
    public List<ScanFilter> getFilter(String addrs) {
        if (addrs == null || addrs.isEmpty()) {
            return new ArrayList<>();
        }
        List<ScanFilter> ret = new ArrayList<>();
        String[] pieces = addrs.split(",");
        for (String piece : pieces) {
            try {
                ScanFilter filt = new ScanFilter.Builder()
                        .setDeviceAddress(piece.trim())
                        .build();
                ret.add(filt);
            } catch (IllegalArgumentException e) {}
        }
        return ret;
    }

    public ScanSettings makeSettings() {
        return new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
    }
}