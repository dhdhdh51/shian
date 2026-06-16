package com.shivam.safecallreminder;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class InCallActivity extends Activity {
    private TextView numberText;
    private TextView stateText;
    private Button answerButton;
    private Button holdButton;
    private Button speakerButton;
    private Button endButton;
    private boolean speakerOn = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            updateUi();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prepareWindow();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
        handler.post(updater);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(updater);
        super.onPause();
    }

    private void prepareWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(36), dp(22), dp(24));
        root.setBackgroundColor(Color.rgb(15, 23, 42));

        TextView title = new TextView(this);
        title.setText("SafeCall Phone");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrap());

        numberText = new TextView(this);
        numberText.setTextColor(Color.WHITE);
        numberText.setTextSize(24);
        numberText.setGravity(Gravity.CENTER_HORIZONTAL);
        numberText.setPadding(0, dp(28), 0, dp(8));
        root.addView(numberText, matchWrap());

        stateText = new TextView(this);
        stateText.setTextColor(Color.rgb(203, 213, 225));
        stateText.setTextSize(18);
        stateText.setGravity(Gravity.CENTER_HORIZONTAL);
        stateText.setPadding(0, 0, 0, dp(28));
        root.addView(stateText, matchWrap());

        answerButton = button("✅ Answer", Color.rgb(22, 163, 74));
        holdButton = button("⏸ Hold / Unhold", Color.rgb(37, 99, 235));
        speakerButton = button("🔊 Speaker Off", Color.rgb(79, 70, 229));
        endButton = button("🔴 End / Reject", Color.rgb(220, 38, 38));

        root.addView(answerButton);
        root.addView(holdButton);
        root.addView(speakerButton);
        root.addView(endButton);

        answerButton.setOnClickListener(v -> CallManager.answer());
        holdButton.setOnClickListener(v -> CallManager.holdOrUnhold());
        speakerButton.setOnClickListener(v -> toggleSpeaker());
        endButton.setOnClickListener(v -> {
            CallManager.endOrReject();
            toast("Call end/reject request sent");
        });

        setContentView(root);
    }

    private void updateUi() {
        int state = CallManager.getState();
        numberText.setText(CallManager.getNumber());
        stateText.setText(CallManager.stateLabel(state));

        boolean hasCall = CallManager.hasCall();
        answerButton.setEnabled(hasCall && state == Call.STATE_RINGING);
        holdButton.setEnabled(hasCall && (state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING));
        speakerButton.setEnabled(hasCall);
        endButton.setEnabled(hasCall);

        answerButton.setAlpha(answerButton.isEnabled() ? 1.0f : 0.45f);
        holdButton.setAlpha(holdButton.isEnabled() ? 1.0f : 0.45f);
        speakerButton.setAlpha(speakerButton.isEnabled() ? 1.0f : 0.45f);
        endButton.setAlpha(endButton.isEnabled() ? 1.0f : 0.45f);

        if (!hasCall) {
            stateText.setText("No active call");
        }
    }

    private void toggleSpeaker() {
        speakerOn = !speakerOn;
        SafeInCallService service = SafeInCallService.instance;
        if (service != null) {
            service.setSpeaker(speakerOn);
            speakerButton.setText(speakerOn ? "🔊 Speaker On" : "🔊 Speaker Off");
        } else {
            toast("Call service ready nahi hai");
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private Button button(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(16);
        btn.setBackgroundColor(color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, dp(8));
        btn.setLayoutParams(params);
        return btn;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
