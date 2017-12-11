package com.gtafe.usbconnect;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public Intent mIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.serial_console);
        mIntent = new Intent(this, UsbConnectService.class);
        startService(mIntent);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(mIntent);
    }
}
