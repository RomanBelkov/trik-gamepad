package com.trik.crab;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends Activity implements SensorEventListener {
    GestureDetector         mGestureDetector;

    private SensorManager   mSensorManager;
    private int             mAngle;               // -100% ... +100%
    private boolean         mWheelEnabled = false;
    protected SenderService mSender;

    public MainActivity() {
        // mGestureDetector = new GestureDetector(new ControlGestureListner());
        mSender = new SenderService();
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (!mWheelEnabled)
                return;
            synchronized (this) {
                processSensor(event.values);
            }
        } else {
            Log.i("Sensor", "" + event.sensor.getType());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menuSettings:
            Intent settings = new Intent(this, SettingsActivity.class);
            startActivity(settings);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        {
            final ToggleButton tglConnect = (ToggleButton) findViewById(R.id.tglConnect);
            tglConnect.setOnClickListener(new ToggleButton.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean connected;
                    String toastText;
                    if (!tglConnect.isChecked())
                    {
                        mSender.disconnect();
                        connected = false;
                        toastText = "Disconnected.";
                    } else {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                        String addr = prefs.getString(SettingsActivity.SK_HOST_ADDRESS, "127.0.0.1");
                        connected = mSender.connect(addr);
                        toastText = "Connection " + (connected ? "established." : "error.");
                    }
                    tglConnect.setChecked(connected);
                    Toast.makeText(getBaseContext(), toastText, Toast.LENGTH_SHORT).show();
                }

            });
        }

        {
            final ToggleButton tglWheel = (ToggleButton) findViewById(R.id.tglWheel);
            tglWheel.setOnClickListener(new ToggleButton.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mWheelEnabled = tglWheel.isChecked();
                    Toast.makeText(getBaseContext(), "Wheel turned " + (mWheelEnabled ? "ON" : "OFF"),
                            Toast.LENGTH_SHORT)
                            .show();
                }
            });
        }

        setListnersForMovementButtons();
        setListnersForArmButtons();

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    private void setCommand(int buttonId, final String commands[]) {
        final Button btn = (Button) findViewById(buttonId);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < commands.length; ++i)
                    mSender.send(commands[i]);
            }
        });
    }

    private void setListnersForMovementButtons() {
        setCommand(R.id.btnForward, new String[] { "left 100", "right 100" });
        setCommand(R.id.btnBackward, new String[] { "left -100", "right -100" });
        setCommand(R.id.btnLeft, new String[] { "left -100", "right 100" });
        setCommand(R.id.btnRight, new String[] { "left 100", "right -100" });
        setCommand(R.id.btnStop, new String[] { "left 0", "right 0" });
    }

    private void setListnersForArmButtons() {
        setCommand(R.id.btnUp, new String[] { "arm 100" });
        setCommand(R.id.btnDown, new String[] { "arm -100" });
        setCommand(R.id.btnATake, new String[] { "hand 100" });
        setCommand(R.id.btnADrop, new String[] { "hand -100" });
        setCommand(R.id.btnAStop, new String[] { "hand 0", "arm 0" });

        // private
    }

    private void processSensor(float[] current) {
        final float WHEEL_BOOSTER_MULTIPLIER = 1.5f;
        final float x = current[0];
        final float y = current[1];

        int angle = (int) (200 * WHEEL_BOOSTER_MULTIPLIER * Math.atan(y / x) / Math.PI);

        if (angle > 100) {
            angle = 100;
        } else if (angle < -100) {
            angle = -100;
        }

        if (Math.abs(angle) < 20 * WHEEL_BOOSTER_MULTIPLIER
                || Math.abs(mAngle - angle) < 5 * WHEEL_BOOSTER_MULTIPLIER) {
            return;
        }

        mAngle = angle;
        mSender.send("left " + mAngle);
        mSender.send("right " + (-mAngle));
    }

    @Override
    protected void onStop() {
        mSensorManager.unregisterListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ALL),
                SensorManager.SENSOR_DELAY_NORMAL);

    }

    private void setCarAngle(int value) {
        final int SENSITIVITY_PERCENT = 4;
        final int angle = Math.max(-100, Math.min(100, value));
        if (Math.abs(mAngle - angle) > SENSITIVITY_PERCENT) {
            mAngle = angle;
            mSender.send("angle " + mAngle);
        }
    }

}