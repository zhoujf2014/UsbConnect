package com.gtafe.usbconnect;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by ZhouJF on 2017/12/11.
 */

public class UsbConnectService extends Service {
    private static final String TAG = "UsbConnectService";
    public static final String ACTION_DEVICE_PERMISSION = "com.linc.USB_PERMISSION";

    private UsbSerialPort sPort = null;

    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {
                    receivedData(data);

                }
            };
    public UsbManager mUsbManager;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, usbFilter);
        initPort();


    }

    private void initPort() {

        List<UsbSerialDriver> drivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
        if (drivers == null || drivers.size() == 0) {
            return;
        }
        UsbSerialDriver usbSerialDriver = drivers.get(0);
        if (usbSerialDriver == null) {
            return;
        }
        List<UsbSerialPort> ports = usbSerialDriver.getPorts();
        if (ports == null || ports.size() == 0) {
            return;
        }
        sPort = ports.get(0);
        if (sPort == null) {
            onDeviceStateChange();
        }
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (!usbManager.hasPermission(sPort.getDriver().getDevice())) {
            usbManager.requestPermission(sPort.getDriver().getDevice(), PendingIntent.getBroadcast(this, 0, new Intent(ACTION_DEVICE_PERMISSION), 0));
            return;
        }
        UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
        if (connection == null) {
            return;
        }
        try {
            sPort.open(connection);
            sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            onDeviceStateChange();
        } catch (IOException e) {
            Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
            try {
                sPort.close();
            } catch (IOException e2) {
                // Ignore.
            }
            sPort = null;

            return;
        }
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void receivedData(byte[] data) {
        Log.e(TAG, "receivedData: " + "Read " + data.length + " bytes: "
                + HexDump.dumpHexString(data));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
        }

        unregisterReceiver(mUsbReceiver);

    }


    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e(TAG, "onReceive: " + action);
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                //usb设备连接
                initPort();

            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                //usb设备断开
                stopIoManager();
                if (sPort != null) {
                    try {
                        sPort.close();
                    } catch (IOException e) {
                        // Ignore.
                    }
                    sPort = null;
                }
            }
        }
    };
}
