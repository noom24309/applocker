package com.ads.control.listener;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Handler;

import com.ads.control.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VolumeSettingsContentObserver extends ContentObserver {
    private int previousVolume;
    private final Context context;
    private final List<String> code, codeCheck;

    public VolumeSettingsContentObserver(Context context, Handler handler) {
        super(handler);
        this.context = context;
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        previousVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        code = new ArrayList<>();
        String[] arr = {"up", "up", "up", "down", "up", "down", "down", "up"};
        codeCheck = Arrays.asList(arr);
    }

    private boolean checkDeviceIsCharging() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, intentFilter);

        int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
    }

    @Override
    public boolean deliverSelfNotifications() {
        return super.deliverSelfNotifications();
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);

        int delta = previousVolume - currentVolume;

        if (delta > 0) {
            previousVolume = currentVolume;
            code.add(context.getString(R.string.volume_down));
        } else if (delta < 0) {
            previousVolume = currentVolume;
            code.add(context.getString(R.string.volume_up));
        }

        if (code.size() == 8 && code.equals(codeCheck) && checkDeviceIsCharging()) {

        }
    }
}
