package com.health.medisync;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LogDoseActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Schedule data passed in from MainActivity
    private String docId, pillName, amount, notifTime, foodOption, days;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_dose);

        // ── 1. Firebase ────────────────────────────────────────────────
        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // ── 2. Unpack Intent extras ────────────────────────────────────
        docId      = getIntent().getStringExtra("docId");
        pillName   = getIntent().getStringExtra("pillName");
        amount     = getIntent().getStringExtra("amount");
        notifTime  = getIntent().getStringExtra("notifTime");
        foodOption = getIntent().getStringExtra("foodOption");
        days       = getIntent().getStringExtra("days");

        // ── 3. Populate UI ─────────────────────────────────────────────
        TextView tvDrugName   = findViewById(R.id.tv_drug_name);
        TextView tvTime       = findViewById(R.id.tv_time);
        TextView tvInstruction = findViewById(R.id.tv_instruction);

        tvDrugName.setText(pillName != null ? pillName : "Medication");
        tvTime.setText(notifTime != null && !notifTime.isEmpty() ? notifTime : "--:--");

        // Build instruction from amount + food option
        String instruction = "";
        if (amount != null && !amount.isEmpty()) {
            instruction += "Take " + amount + " tablet(s)";
        }
        if (foodOption != null && !foodOption.isEmpty()) {
            instruction += (instruction.isEmpty() ? "" : " ") + foodOption.toLowerCase() + " with water";
        }
        tvInstruction.setText(instruction.isEmpty() ? "Follow prescription instructions" : instruction);

        // ── 4. Back Button ─────────────────────────────────────────────
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());



        // ── 6. Taken ───────────────────────────────────────────────────
        findViewById(R.id.btn_taken).setOnClickListener(v ->
                logDose("Taken"));

        // ── 7. Skipped ─────────────────────────────────────────────────
        findViewById(R.id.btn_skipped).setOnClickListener(v ->
                logDose("Skipped"));

        // ── 8. Delay (logs as Delayed, stays on screen) ───────────────
        findViewById(R.id.btn_delay).setOnClickListener(v ->
                logDose("Delayed"));

        // ── 9. Done — go back without logging ─────────────────────────
        findViewById(R.id.btn_done).setOnClickListener(v -> finish());
    }

    // ── SAVE LOG TO FIRESTORE ──────────────────────────────────────────
    private void logDose(String status) {
        String userId = mAuth.getUid();
        if (userId == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> log = new HashMap<>();
        log.put("scheduleId", docId);
        log.put("pillName", pillName);
        log.put("status", status);          // "Taken" | "Skipped" | "Delayed"
        log.put("timestamp", System.currentTimeMillis());

        // Save under: users -> userId -> logs -> (auto ID)
        db.collection("users")
                .document(userId)
                .collection("logs")
                .add(log)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                pillName + " marked as " + status,
                                Toast.LENGTH_SHORT).show();
                        finish(); // Return to MainActivity (onResume reloads list)
                    } else {
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error";
                        Toast.makeText(this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
