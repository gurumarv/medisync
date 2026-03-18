package com.health.medisync;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // ── MUST be before super.onCreate() so theme applies correctly ─
        android.content.SharedPreferences prefs =
                getSharedPreferences("MediSyncPrefs", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_mode", false);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                isDark
                        ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                        : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();

        // ── Populate username from Firebase ───────────────────────────
        FirebaseUser user = mAuth.getCurrentUser();
        TextView tvUserName = findViewById(R.id.tv_user_name);
        if (tvUserName != null && user != null) {
            String name = user.getDisplayName();
            if (name == null || name.isEmpty()) {
                name = user.getEmail().split("@")[0];
            }
            tvUserName.setText(name);
        }

        // ── Back Button ────────────────────────────────────────────────
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // ── Export Report row ──────────────────────────────────────────
        findViewById(R.id.tv_export).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        // ── Notifications Switch ───────────────────────────────────────
        SwitchCompat switchNotifications = findViewById(R.id.switch_notifications);
        if (switchNotifications != null) {
            switchNotifications.setOnCheckedChangeListener((btn, isChecked) ->
                    Toast.makeText(this,
                            "Notifications " + (isChecked ? "enabled" : "disabled"),
                            Toast.LENGTH_SHORT).show());
        }

        // ── Dark Mode Switch ───────────────────────────────────────────
        SwitchCompat switchDarkMode = findViewById(R.id.switch_dark_mode);
        if (switchDarkMode != null) {
            // Reflect current saved state in the switch
            switchDarkMode.setChecked(isDark);

            switchDarkMode.setOnCheckedChangeListener((btn, isChecked) -> {
                // 1. Save preference
                prefs.edit().putBoolean("dark_mode", isChecked).apply();

                // 2. Apply theme globally
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        isChecked
                                ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                                : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);

                // 3. Recreate this screen so it redraws in the new theme
                recreate();
            });
        }

        // ── Terms & Conditions row ─────────────────────────────────────
        findViewById(R.id.tv_terms).setOnClickListener(v ->
                Toast.makeText(this, "Terms & Conditions", Toast.LENGTH_SHORT).show());

        // ── Help row ──────────────────────────────────────────────────
        findViewById(R.id.tv_help).setOnClickListener(v ->
                Toast.makeText(this, "Help & Support", Toast.LENGTH_SHORT).show());
    }
}