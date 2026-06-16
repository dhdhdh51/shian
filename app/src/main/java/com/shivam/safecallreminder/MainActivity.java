package com.shivam.safecallreminder;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.view.View;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_DEFAULT_DIALER = 501;
    private static final int REQUEST_CALL_PERMISSION = 502;
    private EditText numberInput;
    private EditText delayInput;
    private EditText maxRetryInput;
    private CheckBox autoRedialCheckBox;
    private TextView statusText;
    private TextView attemptText;
    private TextView countdownText;
    private Button defaultDialerButton;
    private Button startButton;
    private Button retryButton;
    private Button answeredButton;
    private Button stopButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int attempts = 0;
    private int maxRetries = 3;
    private boolean taskActive = false;
    private boolean autoRedialEnabled = true;
    private String currentNumber = "";

    // Countdown runnable
    private Runnable countdownRunnable;
    private int countdownSecondsLeft = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        fillNumberFromDialIntent(getIntent());
        refreshDefaultDialerStatus();
        updateStatus("Ready. Number enter karke Start dabao.");
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        scrollView.addView(root);

        // Title
        TextView title = new TextView(this);
        title.setText("Safe Call Reminder");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(15, 23, 42));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, dp(4));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Auto Redial — call missed ho toh khud se dobara dial karega");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(100, 116, 139));
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setPadding(0, 0, 0, dp(20));
        root.addView(subtitle);

        // Inputs
        numberInput   = input("Phone number (e.g. 9876543210)", InputType.TYPE_CLASS_PHONE);
        delayInput    = input("Retry delay (seconds, e.g. 15)", InputType.TYPE_CLASS_NUMBER);
        maxRetryInput = input("Max retries (e.g. 5)", InputType.TYPE_CLASS_NUMBER);
        delayInput.setText("15");
        maxRetryInput.setText("5");
        root.addView(numberInput);
        root.addView(delayInput);
        root.addView(maxRetryInput);

        // Auto Redial Toggle
        autoRedialCheckBox = new CheckBox(this);
        autoRedialCheckBox.setText("  Auto Redial ON (automatic retry karega)");
        autoRedialCheckBox.setTextSize(16);
        autoRedialCheckBox.setTextColor(Color.rgb(30, 41, 59));
        autoRedialCheckBox.setChecked(true);
        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cbParams.setMargins(0, dp(4), 0, dp(16));
        autoRedialCheckBox.setLayoutParams(cbParams);
        autoRedialCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            autoRedialEnabled = isChecked;
            autoRedialCheckBox.setText(isChecked
                    ? "  Auto Redial ON (automatic retry karega)"
                    : "  Auto Redial OFF (manual retry karna hoga)");
            // Show/hide retry button based on mode
            retryButton.setVisibility(isChecked ? View.GONE : View.VISIBLE);
        });
        root.addView(autoRedialCheckBox);

        // Default phone app role
        defaultDialerButton = button("☎  Set as Default Phone App", Color.rgb(79, 70, 229));
        root.addView(defaultDialerButton);

        // Buttons
        startButton    = button("▶  Start Call", Color.rgb(37, 99, 235));
        retryButton    = button("🔄  Busy / No Answer → Retry (Manual)", Color.rgb(245, 158, 11));
        answeredButton = button("✅  Answered → Finish", Color.rgb(22, 163, 74));
        stopButton     = button("⏹  Stop", Color.rgb(239, 68, 68));

        root.addView(startButton);
        root.addView(retryButton);
        root.addView(answeredButton);
        root.addView(stopButton);

        // Attempt counter
        attemptText = new TextView(this);
        attemptText.setTextSize(17);
        attemptText.setTextColor(Color.rgb(30, 41, 59));
        attemptText.setPadding(0, dp(16), 0, dp(6));
        root.addView(attemptText);

        // Countdown text (only visible during auto redial wait)
        countdownText = new TextView(this);
        countdownText.setTextSize(22);
        countdownText.setTextColor(Color.rgb(37, 99, 235));
        countdownText.setGravity(Gravity.CENTER_HORIZONTAL);
        countdownText.setPadding(0, dp(4), 0, dp(8));
        countdownText.setVisibility(View.GONE);
        root.addView(countdownText);

        // Status box
        statusText = new TextView(this);
        statusText.setTextSize(15);
        statusText.setTextColor(Color.rgb(51, 65, 85));
        statusText.setPadding(dp(14), dp(14), dp(14), dp(14));
        statusText.setBackgroundColor(Color.WHITE);
        root.addView(statusText);

        // Initial state
        retryButton.setEnabled(false);
        answeredButton.setEnabled(false);
        stopButton.setEnabled(false);
        retryButton.setVisibility(View.GONE); // hidden by default since auto is ON

        defaultDialerButton.setOnClickListener(v -> requestDefaultDialerRole());
        startButton.setOnClickListener(v    -> startCallTask());
        retryButton.setOnClickListener(v    -> scheduleManualRetry());
        answeredButton.setOnClickListener(v -> finishTask());
        stopButton.setOnClickListener(v     -> stopTask());

        setContentView(scrollView);
    }

    // ─────────────────────────────────────────────
    //  CORE LOGIC
    // ─────────────────────────────────────────────

    private void startCallTask() {
        currentNumber = numberInput.getText().toString().trim();
        if (currentNumber.length() < 5) {
            toast("Valid phone number enter karo.");
            return;
        }
        requestCallPermissionIfNeeded();
        maxRetries = parsePositive(maxRetryInput.getText().toString(), 5);
        attempts = 0;
        taskActive = true;
        autoRedialEnabled = autoRedialCheckBox.isChecked();

        retryButton.setEnabled(true);
        answeredButton.setEnabled(true);
        stopButton.setEnabled(true);
        startButton.setEnabled(false);

        placeDialerAttempt();
    }

    /**
     * Called when user manually taps Retry (only in manual mode)
     */
    private void scheduleManualRetry() {
        if (!taskActive) return;
        if (attempts >= maxRetries) {
            updateStatus("⚠️ Max retry limit reach ho gaya. Task stop ho raha hai.");
            stopTask();
            return;
        }
        int delaySeconds = parsePositive(delayInput.getText().toString(), 15);
        updateStatus("⏳ Retry " + delaySeconds + " seconds baad hoga...");
        retryButton.setEnabled(false);
        startCountdownAndDial(delaySeconds);
    }

    /**
     * Called automatically after returning to app if auto redial is ON
     */
    private void scheduleAutoRedial() {
        if (!taskActive || !autoRedialEnabled) return;
        if (attempts >= maxRetries) {
            updateStatus("⚠️ Max " + maxRetries + " retries ho gaye. Task stop.");
            stopTask();
            return;
        }
        int delaySeconds = parsePositive(delayInput.getText().toString(), 15);
        updateStatus("🔄 Auto Redial: " + delaySeconds + " seconds mein dobara call hoga...");
        startCountdownAndDial(delaySeconds);
    }

    /**
     * Common countdown + dial logic
     */
    private void startCountdownAndDial(int delaySeconds) {
        cancelCountdown();
        countdownSecondsLeft = delaySeconds;
        countdownText.setVisibility(View.VISIBLE);
        countdownText.setText("📞 Calling in " + countdownSecondsLeft + "s...");

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (!taskActive) {
                    countdownText.setVisibility(View.GONE);
                    return;
                }
                countdownSecondsLeft--;
                if (countdownSecondsLeft > 0) {
                    countdownText.setText("📞 Calling in " + countdownSecondsLeft + "s...");
                    handler.postDelayed(this, 1000);
                } else {
                    countdownText.setVisibility(View.GONE);
                    placeDialerAttempt();
                }
            }
        };
        handler.postDelayed(countdownRunnable, 1000);
    }

    private void cancelCountdown() {
        if (countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
        countdownText.setVisibility(View.GONE);
    }

    private void placeDialerAttempt() {
        attempts++;
        attemptText.setText("Attempt: " + attempts + " / " + maxRetries);
        updateStatus("📲 Dialer open ho raha hai... (Attempt " + attempts + ")");

        Uri callUri = Uri.parse("tel:" + Uri.encode(currentNumber));

        try {
            if (isDefaultDialer()) {
                TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
                if (telecomManager != null) {
                    telecomManager.placeCall(callUri, new Bundle());
                    return;
                }
            }

            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(callUri);
            startActivity(intent);
        } catch (SecurityException se) {
            // Permission/role missing ho toh number filled dialer khul jayega
            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
            dialIntent.setData(callUri);
            try {
                startActivity(dialIntent);
            } catch (Exception e2) {
                updateStatus("❌ Dialer open nahi ho paya: " + e2.getMessage());
            }
        } catch (Exception e) {
            updateStatus("❌ Error: " + e.getMessage());
        }
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        fillNumberFromDialIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDefaultDialerStatus();
        // App pe wapas aaya — agar auto redial ON hai aur task active hai
        // toh auto schedule kar do
        if (taskActive && autoRedialEnabled && countdownRunnable == null) {
            // Small delay so user app dekh sake
            handler.postDelayed(() -> {
                if (taskActive && autoRedialEnabled) {
                    updateStatus("📱 App pe wapas aaye. Auto Redial schedule ho raha hai...");
                    scheduleAutoRedial();
                }
            }, 1500);
        }

        // Manual mode mein retry button enable karo
        if (taskActive && !autoRedialEnabled) {
            retryButton.setEnabled(true);
            updateStatus("📱 App pe wapas aaye. 'Retry' dabao agar answer nahi hua.");
        }
    }

    private void fillNumberFromDialIntent(Intent intent) {
        if (intent == null || numberInput == null) return;
        Uri data = intent.getData();
        if (data != null && "tel".equalsIgnoreCase(data.getScheme())) {
            String number = data.getSchemeSpecificPart();
            if (number != null && number.trim().length() > 0) {
                numberInput.setText(number.trim());
            }
        }
    }

    private void requestDefaultDialerRole() {
        if (isDefaultDialer()) {
            toast("Ye app already Default Phone App hai.");
            refreshDefaultDialerStatus();
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager roleManager = (RoleManager) getSystemService(RoleManager.class);
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    Intent roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);
                    startActivityForResult(roleIntent, REQUEST_DEFAULT_DIALER);
                } else {
                    toast("Is device par dialer role available nahi hai.");
                }
            } else {
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
                startActivityForResult(intent, REQUEST_DEFAULT_DIALER);
            }
        } catch (Exception e) {
            updateStatus("❌ Default dialer request failed: " + e.getMessage());
        }
    }

    private boolean isDefaultDialer() {
        try {
            TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            return telecomManager != null && getPackageName().equals(telecomManager.getDefaultDialerPackage());
        } catch (Exception e) {
            return false;
        }
    }

    private void refreshDefaultDialerStatus() {
        if (defaultDialerButton == null) return;
        if (isDefaultDialer()) {
            defaultDialerButton.setText("✅ Default Phone App Active");
            defaultDialerButton.setEnabled(false);
        } else {
            defaultDialerButton.setText("☎  Set as Default Phone App");
            defaultDialerButton.setEnabled(true);
        }
    }

    private void requestCallPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQUEST_CALL_PERMISSION);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DEFAULT_DIALER) {
            refreshDefaultDialerStatus();
            updateStatus(isDefaultDialer()
                    ? "✅ Default Phone App set ho gaya. Ab in-call screen app ke andar aayegi."
                    : "ℹ️ Default Phone App set nahi hua. Button se dobara try kar sakte ho.");
        }
    }
    private void finishTask() {
        cancelCountdown();
        updateStatus("✅ Call answered! Task complete. Total attempts: " + attempts);
        stopTaskControlsOnly();
    }

    private void stopTask() {
        cancelCountdown();
        updateStatus("⏹ Task stopped. Total attempts: " + attempts);
        stopTaskControlsOnly();
    }

    private void stopTaskControlsOnly() {
        taskActive = false;
        handler.removeCallbacksAndMessages(null);
        retryButton.setEnabled(false);
        answeredButton.setEnabled(false);
        stopButton.setEnabled(false);
        startButton.setEnabled(true);
    }

    // ─────────────────────────────────────────────
    //  UI HELPERS
    // ─────────────────────────────────────────────

    private void updateStatus(String status) {
        if (statusText != null) statusText.setText(status);
        if (attemptText != null && attempts == 0)
            attemptText.setText("Attempt: 0 / " + maxRetries);
    }

    private EditText input(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setTextSize(16);
        editText.setInputType(inputType);
        editText.setSingleLine(true);
        editText.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(12));
        editText.setLayoutParams(params);
        return editText;
    }

    private Button button(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setBackgroundColor(color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(6), 0, dp(6));
        btn.setLayoutParams(params);
        return btn;
    }

    private int parsePositive(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(1, parsed);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
