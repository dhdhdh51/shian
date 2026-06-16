package com.shivam.safecallreminder;

import android.content.Intent;
import android.telecom.Call;
import android.telecom.InCallService;

public class SafeInCallService extends InCallService {
    public static SafeInCallService instance;

    private final Call.Callback callCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            super.onStateChanged(call, state);
            CallManager.setCall(call);
            openCallScreen();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        CallManager.setCall(call);
        try {
            call.registerCallback(callCallback);
        } catch (Exception ignored) {
        }
        openCallScreen();
    }

    @Override
    public void onCallRemoved(Call call) {
        try {
            call.unregisterCallback(callCallback);
        } catch (Exception ignored) {
        }
        CallManager.clearCall(call);
        openCallScreen();
        super.onCallRemoved(call);
    }

    public void setSpeaker(boolean enabled) {
        try {
            setAudioRoute(enabled
                    ? android.telecom.CallAudioState.ROUTE_SPEAKER
                    : android.telecom.CallAudioState.ROUTE_EARPIECE);
        } catch (Exception ignored) {
        }
    }

    private void openCallScreen() {
        Intent intent = new Intent(this, InCallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }
}
