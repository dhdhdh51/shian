package com.shivam.safecallreminder;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.VideoProfile;

public final class CallManager {
    private static Call currentCall;

    private CallManager() {
        // no instance
    }

    public static synchronized void setCall(Call call) {
        currentCall = call;
    }

    public static synchronized void clearCall(Call call) {
        if (currentCall == call) {
            currentCall = null;
        }
    }

    public static synchronized Call getCall() {
        return currentCall;
    }

    public static synchronized boolean hasCall() {
        return currentCall != null;
    }

    public static synchronized int getState() {
        return currentCall == null ? -1 : currentCall.getState();
    }

    public static synchronized String getNumber() {
        if (currentCall == null || currentCall.getDetails() == null) {
            return "Unknown";
        }
        Uri handle = currentCall.getDetails().getHandle();
        if (handle == null) {
            return "Unknown";
        }
        String part = handle.getSchemeSpecificPart();
        return part == null || part.length() == 0 ? handle.toString() : part;
    }

    public static synchronized void answer() {
        if (currentCall != null) {
            currentCall.answer(VideoProfile.STATE_AUDIO_ONLY);
        }
    }

    public static synchronized void endOrReject() {
        if (currentCall == null) return;
        int state = currentCall.getState();
        if (state == Call.STATE_RINGING) {
            currentCall.reject(false, null);
        } else {
            currentCall.disconnect();
        }
    }

    public static synchronized void holdOrUnhold() {
        if (currentCall == null) return;
        if (currentCall.getState() == Call.STATE_HOLDING) {
            currentCall.unhold();
        } else if (currentCall.getState() == Call.STATE_ACTIVE) {
            currentCall.hold();
        }
    }

    public static String stateLabel(int state) {
        switch (state) {
            case Call.STATE_NEW:
                return "New";
            case Call.STATE_RINGING:
                return "Ringing";
            case Call.STATE_DIALING:
                return "Dialing";
            case Call.STATE_CONNECTING:
                return "Connecting";
            case Call.STATE_ACTIVE:
                return "Active";
            case Call.STATE_HOLDING:
                return "On Hold";
            case Call.STATE_DISCONNECTING:
                return "Disconnecting";
            case Call.STATE_DISCONNECTED:
                return "Disconnected";
            case Call.STATE_SELECT_PHONE_ACCOUNT:
                return "Select SIM / Phone Account";
            default:
                return state == -1 ? "No active call" : "State " + state;
        }
    }
}
