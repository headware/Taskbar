/* Copyright 2016 Braden Farmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.farmerbb.taskbar.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ContextThemeWrapper;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.farmerbb.taskbar.BuildConfig;
import com.farmerbb.taskbar.R;
import com.farmerbb.taskbar.service.NotificationService;
import com.farmerbb.taskbar.service.StartMenuService;
import com.farmerbb.taskbar.service.TaskbarService;
import com.farmerbb.taskbar.util.IconCache;
import com.farmerbb.taskbar.util.LauncherHelper;
import com.farmerbb.taskbar.util.U;

public class HomeActivity extends Activity {

    private boolean forceTaskbarStart = false;

    private BroadcastReceiver killReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LauncherHelper.getInstance().setOnHomeScreen(false);

            // Stop the Taskbar and Start Menu services if they should normally not be active
            SharedPreferences pref = U.getSharedPreferences(HomeActivity.this);
            if(!pref.getBoolean("taskbar_active", false) || pref.getBoolean("is_hidden", false)) {
                stopService(new Intent(HomeActivity.this, TaskbarService.class));
                stopService(new Intent(HomeActivity.this, StartMenuService.class));

                IconCache.getInstance(context).clearCache();

                LocalBroadcastManager.getInstance(HomeActivity.this).sendBroadcast(new Intent("com.farmerbb.taskbar.FINISH_FREEFORM_ACTIVITY"));
            }

            finish();
        }
    };

    private BroadcastReceiver forceTaskbarStartReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            forceTaskbarStart = true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        View view = new View(this);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LocalBroadcastManager.getInstance(HomeActivity.this).sendBroadcast(new Intent("com.farmerbb.taskbar.HIDE_START_MENU"));
            }
        });

        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                setWallpaper();
                return false;
            }
        });

        view.setOnGenericMotionListener(new View.OnGenericMotionListener() {
            @Override
            public boolean onGenericMotion(View view, MotionEvent motionEvent) {
                if(motionEvent.getAction() == MotionEvent.ACTION_BUTTON_PRESS
                        && motionEvent.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                    setWallpaper();
                }
                return false;
            }
        });

        final GestureDetector detector = new GestureDetector(this, new GestureDetector.OnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return false;
            }

            @Override
            public void onShowPress(MotionEvent e) {}

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {}

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                return false;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return false;
            }
        });

        detector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                final SharedPreferences pref = U.getSharedPreferences(HomeActivity.this);
                if(getResources().getConfiguration().keyboard == Configuration.KEYBOARD_NOKEYS
                        && !pref.getBoolean("dont_show_double_tap_dialog", false)) {
                    if(pref.getBoolean("double_tap_to_sleep", false)) {
                        U.lockDevice(HomeActivity.this);
                    } else {
                        int theme = -1;
                        switch(pref.getString("theme", "light")) {
                            case "light":
                                theme = R.style.AppTheme;
                                break;
                            case "dark":
                                theme = R.style.AppTheme_Dark;
                                break;
                        }

                        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(HomeActivity.this, theme));
                        builder.setTitle(R.string.double_tap_to_sleep)
                                .setMessage(R.string.enable_double_tap_to_sleep)
                                .setNegativeButton(pref.getBoolean("double_tap_dialog_shown", false)
                                        ? R.string.action_dont_show_again
                                        : R.string.action_cancel, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        pref.edit().putBoolean(pref.getBoolean("double_tap_dialog_shown", false)
                                                ? "dont_show_double_tap_dialog"
                                                : "double_tap_dialog_shown", true).apply();
                                    }
                                })
                                .setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        pref.edit().putBoolean("double_tap_to_sleep", true).apply();
                                        U.lockDevice(HomeActivity.this);
                                    }
                                });

                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                }

                return false;
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return false;
            }

        });

        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                detector.onTouchEvent(event);
                return false;
            }
        });

        setContentView(view);

        LocalBroadcastManager.getInstance(this).registerReceiver(killReceiver, new IntentFilter("com.farmerbb.taskbar.KILL_HOME_ACTIVITY"));
        LocalBroadcastManager.getInstance(this).registerReceiver(forceTaskbarStartReceiver, new IntentFilter("com.farmerbb.taskbar.FORCE_TASKBAR_RESTART"));
    }

    private void setWallpaper() {
        LocalBroadcastManager.getInstance(HomeActivity.this).sendBroadcast(new Intent("com.farmerbb.taskbar.TEMP_HIDE_TASKBAR"));

        try {
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.set_wallpaper)));
        } catch (ActivityNotFoundException e) { /* Gracefully fail */ }
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.N)
    @Override
    protected void onResume() {
        super.onResume();

        if(bootToFreeform()) {
            if(U.launcherIsDefault(this)) {
                DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
                Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);

                Intent intent = new Intent(this, InvisibleActivityFreeform.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                startActivity(intent, ActivityOptions.makeBasic().setLaunchBounds(new Rect(display.getWidth(), display.getHeight(), display.getWidth() + 1, display.getHeight() + 1)).toBundle());
            } else {
                U.showToastLong(this, R.string.set_as_default_home);

                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                try {
                    startActivity(homeIntent);
                    finish();
                } catch (ActivityNotFoundException e) { /* Gracefully fail */ }
            }
        } else {
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("com.farmerbb.taskbar.TEMP_SHOW_TASKBAR"));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        LocalBroadcastManager.getInstance(HomeActivity.this).sendBroadcast(new Intent("com.farmerbb.taskbar.HIDE_START_MENU"));

        if(canDrawOverlays()) {
            if(!bootToFreeform()) {
                final LauncherHelper helper = LauncherHelper.getInstance();
                helper.setOnHomeScreen(true);

                if(forceTaskbarStart) {
                    forceTaskbarStart = false;
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            helper.setOnHomeScreen(true);
                            startTaskbar();
                        }
                    }, 250);
                } else
                    startTaskbar();
            }
        } else {
            ComponentName component = new ComponentName(BuildConfig.APPLICATION_ID, HomeActivity.class.getName());
            getPackageManager().setComponentEnabledSetting(component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);

            finish();
        }
    }

    private boolean bootToFreeform() {
        SharedPreferences pref = U.getSharedPreferences(this);
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && pref.getBoolean("freeform_hack", false)
                && U.hasFreeformSupport(this);
    }

    private void startTaskbar() {
        // We always start the Taskbar and Start Menu services, even if the app isn't normally running
        startService(new Intent(this, TaskbarService.class));
        startService(new Intent(this, StartMenuService.class));

        SharedPreferences pref = U.getSharedPreferences(this);
        if(pref.getBoolean("taskbar_active", false))
            startService(new Intent(this, NotificationService.class));

        // Show the Taskbar temporarily, as nothing else will be visible on screen
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                LocalBroadcastManager.getInstance(HomeActivity.this).sendBroadcast(new Intent("com.farmerbb.taskbar.TEMP_SHOW_TASKBAR"));
            }
        }, 100);
    }

    @Override
    protected void onStop() {
        super.onStop();

        SharedPreferences pref = U.getSharedPreferences(this);
        if(!bootToFreeform()) {
            LauncherHelper.getInstance().setOnHomeScreen(false);

            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("com.farmerbb.taskbar.TEMP_HIDE_TASKBAR"));

            // Stop the Taskbar and Start Menu services if they should normally not be active
            if(!pref.getBoolean("taskbar_active", false) || pref.getBoolean("is_hidden", false)) {
                stopService(new Intent(this, TaskbarService.class));
                stopService(new Intent(this, StartMenuService.class));

                IconCache.getInstance(this).clearCache();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(killReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(forceTaskbarStartReceiver);
    }

    @Override
    public void onBackPressed() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("com.farmerbb.taskbar.HIDE_START_MENU"));
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }
}
