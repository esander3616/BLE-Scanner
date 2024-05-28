package com.example.ble_scanner.ui.device_display;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;

import com.example.ble_scanner.databinding.FragmentDeviceDisplayBinding;

import java.util.Map;

public class DeviceDisplayFragment extends Fragment {
     private FragmentDeviceDisplayBinding binding;
     private Button scanButton;
     private TextView outLog;
     private DeviceDisplayViewModel deviceDisplayViewModel;
     private BluetoothAdapter adapter;
     private BluetoothLeScanner scanner;

     private final ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher =
             registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                  Boolean scanGranted = result.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false);
                  Boolean connectGranted = result.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false);
                  if (scanGranted != null && scanGranted && connectGranted != null && connectGranted) {
                       startBleScan();
                  } else {
                       outLog.append("Permission denied\n");
                  }
             });

     @Override
     public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
          deviceDisplayViewModel = new ViewModelProvider(this).get(DeviceDisplayViewModel.class);
          binding = FragmentDeviceDisplayBinding.inflate(inflater, container, false);
          View root = binding.getRoot();

          scanButton = binding.scanButton;
          outLog = binding.textView;

          adapter = deviceDisplayViewModel.getAdapter(requireContext());
          if (adapter != null) {
               outLog.setText("Adapter: " + adapter.getAddress());
               scanner = adapter.getBluetoothLeScanner();
          } else {
               outLog.setText("No adapter obtained");
          }

          deviceDisplayViewModel.getScanResults().observe(getViewLifecycleOwner(), this::displayScanResult);
          deviceDisplayViewModel.getScanStatus().observe(getViewLifecycleOwner(), status -> outLog.append(status + "\n"));

          scanButton.setOnClickListener(v -> {
               if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                       || ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestMultiplePermissionsLauncher.launch(new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                    });
               } else {
                    startBleScan();
               }
          });

          return root;
     }

     private void startBleScan() {
          scanButton.setVisibility(View.GONE);
          deviceDisplayViewModel.scan_ble(scanner, requireContext());
     }

     private void displayScanResult(ScanResult result) {
          if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
               outLog.append("Permission denied for device name access\n");
               return;
          }

          String deviceName = (result.getDevice().getName() != null) ? result.getDevice().getName() : "Unknown Device";
          String deviceInfo = "Device: " + deviceName + " - " + result.getDevice().getAddress() + " - RSSI: " + result.getRssi() + "\n";
          outLog.append(deviceInfo);
     }
}