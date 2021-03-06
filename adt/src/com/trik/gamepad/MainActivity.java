package com.trik.gamepad;

import java.net.URI;
import java.net.URISyntaxException;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import com.demo.mjpeg.MjpegView;
import com.trik.gamepad.SenderService.OnEventListener;

public class MainActivity extends ActionBarActivity implements SensorEventListener {

    static final String                                TAG        = "MainActivity";

    SensorManager                                      mSensorManager;
    int                                                mAngle;                     // -100%
    // ...
    // +100%
    boolean                                            mWheelEnabled;
    SenderService                                      mSender;
    SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferencesListener;

    int                                                mWheelStep = 7;

    MjpegView                                          mVideo;

    URI                                                mVideoURI;

    // @SuppressWarnings("deprecation")
    // @TargetApi(16)
    private void createPad(int id, String strId) {
        final SquareTouchPadLayout pad = (SquareTouchPadLayout) findViewById(id);
        pad.setPadName("pad " + strId);
        pad.setSender(mSender);
        // if (android.os.Build.VERSION.SDK_INT >= 16) {
        // pad.setBackground(image);
        // } else {
        // pad.setBackgroundDrawable(image);
        // }
    };

    @Override
    public void onAccuracyChanged(final Sensor arg0, final int arg1) {
        // TODO Auto-generated method stub

    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setSystemUiVisibility(false);
        {
            ActionBar a = getSupportActionBar();

            a.setDisplayShowHomeEnabled(true);
            a.setDisplayUseLogoEnabled(true);
            a.setLogo(R.drawable.trik_icon);

            a.setDisplayShowTitleEnabled(true);
            a.setTitle("Preferences");

        }

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSender = new SenderService(this);

        {
            mVideo = (MjpegView) findViewById(R.id.video);
            mVideo.setOverlayPosition(MjpegView.POSITION_UPPER_RIGHT);
            mVideo.showFps(true);
            mVideo.setDisplayMode(MjpegView.SIZE_BEST_FIT);

        }

        recreateMagicButtons(5);

        {
            mSender.setOnDiconnectedListner(new OnEventListener<String>() {
                @Override
                public void onEvent(final String reason) {
                    toast("Disconnected." + reason);
                }
            });
        }

        /*
         * { final ToggleButton tglWheel = (ToggleButton)
         * findViewById(R.id.tglWheel); tglWheel.setOnCheckedChangeListener(new
         * CompoundButton.OnCheckedChangeListener() {
         * 
         * @Override public void onCheckedChanged(final CompoundButton
         * buttonView, final boolean isChecked) { mWheelEnabled = isChecked; }
         * }); }
         */

        {
            final Button btnSettings = (Button) findViewById(R.id.btnSettings);
            btnSettings.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(final View v) {
                    android.support.v7.app.ActionBar a = getSupportActionBar();
                    setSystemUiVisibility(!a.isShowing());
                }
            });

        }

        {
            final View controlsOverlay = findViewById(R.id.controlsOverlay);
            controlsOverlay.bringToFront();
        }

        {
            createPad(R.id.leftPad, "1");
            createPad(R.id.rightPad, "2");
        }

        {

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            mSharedPreferencesListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                float mPrevAlpha;

                @Override
                public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
                    final String addr = sharedPreferences.getString(SettingsActivity.SK_HOST_ADDRESS, "192.168.1.1");
                    int portNumber = 4444;
                    final String portStr = sharedPreferences.getString(SettingsActivity.SK_HOST_PORT, "4444");
                    try {
                        portNumber = Integer.parseInt(portStr);
                    } catch (final NumberFormatException e) {
                        toast("Port number '" + portStr + "' is incorrect.");
                    }
                    final String oldAddr = mSender.getHostAddr();
                    mSender.setTarget(addr, portNumber);

                    if (!addr.equalsIgnoreCase(oldAddr)) {
                        // update video stream URI when target addr changed
                        sharedPreferences.edit()
                                .putString(SettingsActivity.SK_VIDEO_URI, "http://" + addr + ":8080/?action=stream")
                                .commit();
                    }

                    {
                        final int defAlpha = 100;
                        int padsAlpha = defAlpha;

                        try {
                            padsAlpha = Integer.parseInt(sharedPreferences.getString(SettingsActivity.SK_SHOW_PADS,
                                    String.valueOf(defAlpha)));
                        } catch (NumberFormatException nfe) {
                            padsAlpha = defAlpha;
                        }

                        final float alpha = Math.max(0, Math.min(255, padsAlpha)) / 255.0f;
                        AlphaAnimation alphaUp = new AlphaAnimation(mPrevAlpha, alpha);
                        mPrevAlpha = alpha;
                        alphaUp.setFillAfter(true);
                        alphaUp.setDuration(2000);
                        findViewById(R.id.controlsOverlay).startAnimation(alphaUp);
                        findViewById(R.id.buttons).startAnimation(alphaUp);
                    }

                    {
                        // "http://trackfield.webcam.oregonstate.edu/axis-cgi/mjpg/video.cgi?resolution=320x240";

                        String videoStreamURI = sharedPreferences.getString(SettingsActivity.SK_VIDEO_URI, "http://"
                                + addr + ":8080/?action=stream");

                        // --no-sout-audio --sout
                        // "#transcode{width=320,height=240,vcodec=mp2v,fps=20}:rtp{ttl=5,sdp=rtsp://:8889/s}"
                        // works only with vcodec=mp4v without audio :(

                        // http://developer.android.com/reference/android/media/MediaPlayer.html
                        // http://developer.android.com/guide/appendix/media-formats.html

                        try {
                            mVideoURI = videoStreamURI == null || "".equals(videoStreamURI) ? null : new URI(
                                    videoStreamURI);
                        } catch (URISyntaxException e) {
                            toast("Illegal video stream URI\n" + e.getReason());
                            mVideoURI = null;
                        }

                    }

                    {
                        mWheelStep = Integer
                                .getInteger(
                                        sharedPreferences.getString(SettingsActivity.SK_WHEEL_STEP,
                                                String.valueOf(mWheelStep)), mWheelStep);
                        mWheelStep = Math.max(1, Math.min(100, mWheelStep));
                    }

                }
            };
            mSharedPreferencesListener.onSharedPreferenceChanged(prefs, SettingsActivity.SK_HOST_ADDRESS);
            prefs.registerOnSharedPreferenceChangeListener(mSharedPreferencesListener);
        }

    };

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);

        // TODO: remove this hack
        final CheckBox w = (CheckBox) MenuItemCompat.getActionView(menu.findItem(R.id.wheel));
        w.setText("WHEEL");

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
        case R.id.settings:
            final Intent settings = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(settings);
            return true;
        case R.id.wheel:
            mWheelEnabled = !mWheelEnabled;
            item.setChecked(mWheelEnabled);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }

    }

    @Override
    protected void onPause() {
        mSensorManager.unregisterListener(this);
        mSender.disconnect("Inactive gamepad");
        mVideo.stopPlayback();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        new StartReadMjpegAsync(mVideo).execute(mVideoURI);

        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ALL),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (!mWheelEnabled)
                return;
            processSensor(event.values);
        } else {
            Log.i("Sensor", "" + event.sensor.getType());
        }
    }

    private void processSensor(final float[] values) {
        final double WHEEL_BOOSTER_MULTIPLIER = 1.5;
        final double x = values[0];
        final double y = values[1];
        if (x < 1e-6)
            return;

        int angle = (int) (200 * WHEEL_BOOSTER_MULTIPLIER * Math.atan2(y, x) / Math.PI);

        if (Math.abs(angle) < 10) {
            angle = 0;
        } else if (angle > 100) {
            angle = 100;
        } else if (angle < -100) {
            angle = -100;
        }

        if (Math.abs(mAngle - angle) < mWheelStep)
            return;

        mAngle = angle;

        mSender.send("wheel " + mAngle);
    };

    private void recreateMagicButtons(final int count) {
        final ViewGroup buttonsView = (ViewGroup) findViewById(R.id.buttons);
        buttonsView.removeAllViews();
        for (int num = 1; num <= count; ++num) {
            final String name = "" + num;
            final Button btn = new Button(MainActivity.this) {
                private long mWhenDown = 0;
                {
                    setHapticFeedbackEnabled(true);
                    setGravity(Gravity.CENTER);
                    setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

                    setText(name);
                    setBackgroundResource(R.drawable.button_shape);
                    setOnTouchListener(new View.OnTouchListener() {

                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                mWhenDown = System.currentTimeMillis();
                                mSender.send("btn " + name + " down");
                            }
                            return false; // proceed with default codepath
                        }
                    });
                    setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View arg0) {
                            long delay = System.currentTimeMillis() - mWhenDown;
                            mSender.send("btn " + name + " up " + delay);

                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);

                        }
                    });
                }
            };

            buttonsView.addView(btn);
        }
    }

    @SuppressLint("NewApi")
    void setSystemUiVisibility(boolean on) {
        int flags = 0;
        final int sdk = Build.VERSION.SDK_INT;
        if (sdk >= 19) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE;
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        if (sdk >= 16) {
            flags |= on ? 0 : View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

        } else {
            if (on) {
                getSupportActionBar().show();
            } else {
                getSupportActionBar().hide();
            }
        }

        final View mainView = findViewById(R.id.main);
        if (sdk >= 14) {
            flags |= on ? 0 : View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            flags |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
            mainView.setSystemUiVisibility(flags);
        }

        if (on) {
            mainView.postDelayed(new Runnable() {

                @Override
                public void run() {
                    setSystemUiVisibility(false);

                }
            }, 2000);
        }

    }

    void toast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show();
            }
        });
    }

}
