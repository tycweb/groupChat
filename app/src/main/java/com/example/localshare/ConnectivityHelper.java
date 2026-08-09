package com.example.localshare;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

/**
 * Checks Wi-Fi / Bluetooth state and, if either is off, shows a prompt
 * asking the user to enable it (like ShareIt's "turn on Wi-Fi/Bluetooth
 * to share nearby" nudge). Wi-Fi is required for the actual file
 * transfer; Bluetooth is only used to help discover nearby devices.
 */
public class ConnectivityHelper {

    public static void checkAndPrompt(Activity activity) {
        boolean wifiOn = isWifiEnabled(activity);
        boolean btOn = isBluetoothEnabled();

        if (!wifiOn) {
            showEnableDialog(activity, "Wi-Fi is off",
                    "Turn on Wi-Fi so nearby devices can connect to LocalShare.",
                    () -> openWifiSettings(activity));
        } else if (!btOn) {
            showEnableDialog(activity, "Bluetooth is off",
                    "Turning on Bluetooth helps LocalShare discover nearby devices faster.",
                    () -> requestEnableBluetooth(activity));
        }
    }

    private static boolean isWifiEnabled(Activity activity) {
        try {
            WifiManager wifiManager = (WifiManager) activity.getApplicationContext()
                    .getSystemService(Activity.WIFI_SERVICE);
            return wifiManager != null && wifiManager.isWifiEnabled();
        } catch (Exception e) {
            return true; // fail open, don't nag if we can't tell
        }
    }

    private static boolean isBluetoothEnabled() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            return adapter != null && adapter.isEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    private static void showEnableDialog(Activity activity, String title, String message, Runnable onEnable) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Enable", (dialog, which) -> onEnable.run())
                .setNegativeButton("Not now", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private static void openWifiSettings(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activity.startActivity(new Intent(Settings.Panel.ACTION_WIFI));
            } else {
                activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        } catch (Exception e) {
            activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        }
    }

    private static void requestEnableBluetooth(Activity activity) {
        try {
            activity.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } catch (Exception e) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }
}
