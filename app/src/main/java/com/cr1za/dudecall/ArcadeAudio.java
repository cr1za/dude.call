package com.cr1za.dudecall;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/** Tiny generated WAV effects, mixed as media; silence whenever headphones are connected. */
final class ArcadeAudio {
    enum Effect { PRESS, RELEASE, SOFT_RELEASE, ERROR, SPRING, LOCK, CHIME }
    private final SoundPool sounds;
    private final int[] soundIds = new int[Effect.values().length];
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioManager manager;
    private final AudioDeviceCallback deviceCallback;
    private final List<Integer> activeStreams = new ArrayList<>();
    private boolean muted;
    private boolean released;
    private int fadeGeneration;

    ArcadeAudio(Context context) {
        manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        sounds = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attributes).build();
        soundIds[Effect.PRESS.ordinal()] = sounds.load(context, R.raw.press, 1);
        soundIds[Effect.RELEASE.ordinal()] = sounds.load(context, R.raw.release, 1);
        soundIds[Effect.SOFT_RELEASE.ordinal()] = sounds.load(context, R.raw.soft_release, 1);
        soundIds[Effect.ERROR.ordinal()] = sounds.load(context, R.raw.error, 1);
        soundIds[Effect.SPRING.ordinal()] = sounds.load(context, R.raw.spring, 1);
        soundIds[Effect.LOCK.ordinal()] = sounds.load(context, R.raw.lock_in, 1);
        soundIds[Effect.CHIME.ordinal()] = sounds.load(context, R.raw.chime, 1);
        muted = hasHeadphones();
        deviceCallback = new AudioDeviceCallback() {
            @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { handler.post(() -> refreshRoute()); }
            @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { handler.post(() -> refreshRoute()); }
        };
        if (manager != null) manager.registerAudioDeviceCallback(deviceCallback, handler);
    }

    void refreshRoute() {
        boolean next = hasHeadphones();
        if (next && !muted) fadeAll(100);
        muted = next;
    }

    private boolean hasHeadphones() {
        if (manager == null) return false;
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_HEARING_AID:
                    return true;
                default:
                    // BLE headset/speaker type constants are available on Android 12+.
                    if (android.os.Build.VERSION.SDK_INT >= 31 &&
                            (device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                             device.getType() == AudioDeviceInfo.TYPE_BLE_SPEAKER)) return true;
            }
        }
        return false;
    }

    void play(Effect effect, boolean reducedMotion) {
        if (released || muted || (reducedMotion && effect == Effect.SPRING)) return;
        int id = soundIds[effect.ordinal()];
        int stream = sounds.play(id, .55f, .55f, 1, 0, 1f);
        if (stream != 0) activeStreams.add(stream);
        if (activeStreams.size() > 25) activeStreams.subList(0, 12).clear();
    }

    void fadeAll(int milliseconds) {
        if (released || activeStreams.isEmpty()) return;
        int token = ++fadeGeneration;
        List<Integer> streams = new ArrayList<>(activeStreams);
        activeStreams.clear();
        int steps = Math.max(1, milliseconds / 10);
        for (int i = 1; i <= steps; i++) {
            final int index = i;
            handler.postDelayed(() -> {
                if (released || token != fadeGeneration) return;
                float volume = .55f * (1f - index / (float) steps);
                for (int stream : streams) {
                    if (index == steps) sounds.stop(stream);
                    else sounds.setVolume(stream, volume, volume);
                }
            }, (long) milliseconds * i / steps);
        }
    }

    void release() {
        if (released) return;
        released = true;
        if (manager != null) manager.unregisterAudioDeviceCallback(deviceCallback);
        handler.removeCallbacksAndMessages(null);
        sounds.release();
    }
}
