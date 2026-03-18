package com.health.medisync;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AddScheduleActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private EditText etPillName;
    private EditText etAmount;
    private EditText etDays;
    private EditText etNotificationTime;

    // Tracks which food option is selected: 0 = Before, 1 = During, 2 = After
    private int selectedFoodOption = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_schedule);

        // ── 1. Initialize Firebase ─────────────────────────────────────
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // ── 2. Bind Views ──────────────────────────────────────────────
        etPillName         = findViewById(R.id.et_pill_name);
        etAmount           = findViewById(R.id.et_amount);
        etDays             = findViewById(R.id.et_days);
        etNotificationTime = findViewById(R.id.et_notification_time);

        // ── 3. Back Button ─────────────────────────────────────────────
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());


        // ── 5. Food Option Selection ───────────────────────────────────
        findViewById(R.id.card_before_food).setOnClickListener(v -> setFoodOption(0));
        findViewById(R.id.card_during_food).setOnClickListener(v -> setFoodOption(1));
        findViewById(R.id.card_after_food).setOnClickListener(v  -> setFoodOption(2));

        // ── 6. + Button — increment notification time by 30 minutes ───
        findViewById(R.id.btn_add_notification).setOnClickListener(v -> incrementTime30Min());

        // ── 7. Done Button — validate and save ────────────────────────
        findViewById(R.id.btn_done).setOnClickListener(v -> {
            String pillName  = etPillName.getText().toString().trim();
            String amount    = etAmount.getText().toString().trim();
            String days      = etDays.getText().toString().trim();
            String notifTime = etNotificationTime.getText().toString().trim();

            if (pillName.isEmpty()) {
                Toast.makeText(this, "Please enter a pill name", Toast.LENGTH_SHORT).show();
                return;
            }
            if (amount.isEmpty()) {
                Toast.makeText(this, "Please enter the amount", Toast.LENGTH_SHORT).show();
                return;
            }
            if (days.isEmpty()) {
                Toast.makeText(this, "Please enter how many days", Toast.LENGTH_SHORT).show();
                return;
            }

            saveSchedule(pillName, amount, days, notifTime);
        });
    }

    // ── INCREMENT NOTIFICATION TIME BY 30 MINUTES ─────────────────────
    private void incrementTime30Min() {
        String current = etNotificationTime.getText().toString().trim();

        // Parse current time — accepts "10:00 AM" or "10:00"
        int hour = 10, minute = 0;
        boolean isPm = false;

        try {
            // Strip AM/PM and split
            String timePart = current.replaceAll("(?i)\\s*(am|pm).*", "").trim();
            isPm = current.toUpperCase().contains("PM");

            String[] parts = timePart.split(":");
            hour   = Integer.parseInt(parts[0].trim());
            minute = Integer.parseInt(parts[1].trim());

            // Convert 12-hour → 24-hour for Calendar
            if (isPm && hour != 12) hour += 12;
            if (!isPm && hour == 12) hour = 0;

        } catch (Exception e) {
            // Fallback: start from 10:00 AM if parsing fails
            hour   = 10;
            minute = 0;
        }

        // Add 30 minutes using Calendar
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.add(Calendar.MINUTE, 30);

        int newHour   = cal.get(Calendar.HOUR_OF_DAY);
        int newMinute = cal.get(Calendar.MINUTE);

        // Format back to 12-hour AM/PM
        String period  = newHour >= 12 ? "PM" : "AM";
        int display12  = newHour % 12;
        if (display12 == 0) display12 = 12;

        String formatted = String.format("%d:%02d %s", display12, newMinute, period);
        etNotificationTime.setText(formatted);
    }

    // ── SCHEDULE WORKMANAGER REMINDER AT THE CORRECT TIME ────────────
    private void scheduleReminder(String pillName, String notifTime, int durationDays) {
        // Parse "10:00 AM" → hour + minute
        int hour = 10, minute = 0;
        try {
            String timePart = notifTime.replaceAll("(?i)\\s*(am|pm).*", "").trim();
            boolean isPm    = notifTime.toUpperCase().contains("PM");
            String[] parts  = timePart.split(":");
            hour   = Integer.parseInt(parts[0].trim());
            minute = Integer.parseInt(parts[1].trim());
            if (isPm && hour != 12) hour += 12;
            if (!isPm && hour == 12) hour = 0;
        } catch (Exception e) {
            hour = 10; minute = 0; // default 10:00 AM if parsing fails
        }

        // Calculate delay from now until the next occurrence of that time
        java.util.Calendar target = java.util.Calendar.getInstance();
        target.set(java.util.Calendar.HOUR_OF_DAY, hour);
        target.set(java.util.Calendar.MINUTE, minute);
        target.set(java.util.Calendar.SECOND, 0);
        target.set(java.util.Calendar.MILLISECOND, 0);

        // If that time has already passed today, schedule for tomorrow
        if (target.getTimeInMillis() <= System.currentTimeMillis()) {
            target.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }

        long delayMillis = target.getTimeInMillis() - System.currentTimeMillis();

        // Pass pill name to the worker so it can show it in the notification
        androidx.work.Data inputData = new androidx.work.Data.Builder()
                .putString("pillName", pillName)
                .build();

        // Schedule a daily repeating reminder for the duration of the schedule
        androidx.work.PeriodicWorkRequest reminder =
                new androidx.work.PeriodicWorkRequest.Builder(
                        ReminderWorker.class, 1, java.util.concurrent.TimeUnit.DAYS)
                        .setInitialDelay(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .setInputData(inputData)
                        .addTag(pillName) // tag lets us cancel it later if needed
                        .build();

        androidx.work.WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                        pillName + "_reminder",
                        androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                        reminder);
    }

    // ── FOOD OPTION HIGHLIGHT ──────────────────────────────────────────
    private void setFoodOption(int option) {
        selectedFoodOption = option;

        // Reset all to grey
        ((androidx.cardview.widget.CardView) findViewById(R.id.card_before_food))
                .setCardBackgroundColor(android.graphics.Color.parseColor("#EEEEEE"));
        ((androidx.cardview.widget.CardView) findViewById(R.id.card_during_food))
                .setCardBackgroundColor(android.graphics.Color.parseColor("#EEEEEE"));
        ((androidx.cardview.widget.CardView) findViewById(R.id.card_after_food))
                .setCardBackgroundColor(android.graphics.Color.parseColor("#EEEEEE"));

        // Highlight selected one green
        int selectedId = option == 0 ? R.id.card_before_food
                : option == 1 ? R.id.card_during_food
                : R.id.card_after_food;

        ((androidx.cardview.widget.CardView) findViewById(selectedId))
                .setCardBackgroundColor(android.graphics.Color.parseColor("#2ECC71"));
    }

    // ── SAVE TO FIRESTORE ──────────────────────────────────────────────
    private void saveSchedule(String pillName, String amount, String days, String notifTime) {
        String userId = mAuth.getUid();
        if (userId == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String foodLabel = selectedFoodOption == 0 ? "Before Food"
                : selectedFoodOption == 1 ? "During Food"
                : "After Food";

        // Build the data map — mirrors the Medication model fields
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("pillName",          pillName);
        schedule.put("amount",            amount);
        schedule.put("days",              days);
        schedule.put("foodOption",        foodLabel);
        schedule.put("notificationTime",  notifTime);
        schedule.put("createdAt",         System.currentTimeMillis());

        // Save under: users -> userId -> schedules -> (auto-generated ID)
        db.collection("users")
                .document(userId)
                .collection("schedules")
                .add(schedule)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Schedule notification at the user's chosen time
                        scheduleReminder(pillName, notifTime, Integer.parseInt(days));
                        Toast.makeText(this, "Schedule saved!", Toast.LENGTH_SHORT).show();
                        finish(); // Go back to MainActivity
                    } else {
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Failed to save";
                        Toast.makeText(this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}