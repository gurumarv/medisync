package com.health.medisync;

import android.content.Context;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private EditText etMedName, etDosage;

    // Dynamic container – replaces the 3 static XML cards
    private LinearLayout llMedList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // ── 0. Restore dark mode before any view is inflated ──────────
        android.content.SharedPreferences prefs =
                getSharedPreferences("MediSyncPrefs", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_mode", false);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                isDark
                        ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                        : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ── 1. Auth Guard ──────────────────────────────────────────────
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // ── 2. Firebase & Notification Channel ────────────────────────
        db = FirebaseFirestore.getInstance();
        createNotificationChannel();

        // ── 3. Bind Views ──────────────────────────────────────────────
        etMedName = findViewById(R.id.et_med_name);
        etDosage  = findViewById(R.id.et_dosage);
        llMedList = findViewById(R.id.ll_med_list);

        // Username from Firebase
        TextView tvUsername = findViewById(R.id.tv_username);
        String displayName = currentUser.getDisplayName();
        String name = (displayName != null && !displayName.isEmpty())
                ? displayName
                : currentUser.getEmail().split("@")[0];
        tvUsername.setText(name);

        // ── 4. Edge-to-Edge Insets ─────────────────────────────────────
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ── 5. Load schedules → populate card list ─────────────────────
        loadSchedules(currentUser.getUid());

        // ── 6. Notification Permission (Android 13+) ───────────────────
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        // ── 7. Add Schedule FAB ────────────────────────────────────────
        findViewById(R.id.btn_add_schedule).setOnClickListener(v ->
                startActivity(new Intent(this, AddScheduleActivity.class)));

        // ── 8. Bottom Nav Bar ──────────────────────────────────────────
        findViewById(R.id.nav_calendar).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        findViewById(R.id.nav_symptoms).setOnClickListener(v ->
                startActivity(new Intent(this, SymptomCheckerActivity.class)));

        findViewById(R.id.nav_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // ── 9. Hidden Java-required views ─────────────────────────────
        displayAdherenceChart(75f);

        TextView tvStreak = findViewById(R.id.tv_streak);
        tvStreak.setText("Loading...");

        findViewById(R.id.btn_export).setOnClickListener(v -> generatePdf(this));

        findViewById(R.id.btn_check_symptoms).setOnClickListener(v -> {
            List<String> symptoms = new ArrayList<>();
            symptoms.add("Fever");
            symptoms.add("Headache");
            symptoms.add("Body pain");
            String result = matchSymptoms(symptoms);
            ((TextView) findViewById(R.id.tv_symptom_result)).setText(result);
        });

        // ── 10. Hidden save/mark-taken ──────────────────────────────────
        findViewById(R.id.btn_save_med).setOnClickListener(v -> {
            String medName = etMedName.getText().toString().trim();
            String dose    = etDosage.getText().toString().trim();
            if (!medName.isEmpty()) {
                Medication newMed = new Medication(
                        medName.toLowerCase(), medName, dose,
                        "Daily", "After Food", System.currentTimeMillis());
                saveMedication(newMed);
                etMedName.setText("");
                etDosage.setText("");
            }
        });

        findViewById(R.id.btn_mark_taken).setOnClickListener(v -> {
            String medName = etMedName.getText().toString().trim();
            if (!medName.isEmpty()) {
                saveLog(new DoseLog(medName.toLowerCase(), "Taken", System.currentTimeMillis()));
                Toast.makeText(this, "Dose Logged!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── RELOAD LIST WHEN RETURNING FROM AddScheduleActivity ───────────
    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            loadSchedules(currentUser.getUid());
        }
    }

    // ── LOAD SCHEDULES FROM FIRESTORE ─────────────────────────────────
    private void loadSchedules(String userId) {
        db.collection("users")
                .document(userId)
                .collection("schedules")
                .get()
                .addOnCompleteListener(task -> runOnUiThread(() -> {

                    if (isFinishing() || isDestroyed()) return;

                    llMedList.removeAllViews();

                    if (!task.isSuccessful() || task.getResult() == null) {
                        Log.e("Firestore", "Failed to load", task.getException());
                        Toast.makeText(this, "Could not load schedules", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    TextView tvStreak = findViewById(R.id.tv_streak);

                    if (task.getResult().isEmpty()) {
                        TextView empty = new TextView(this);
                        empty.setText("No medications scheduled yet.\nTap + to add one.");
                        empty.setTextColor(android.graphics.Color.parseColor("#999999"));
                        empty.setTextSize(14f);
                        empty.setPadding(4, 16, 4, 16);
                        llMedList.addView(empty);
                        if (tvStreak != null) tvStreak.setText("0 of 0 completed");
                        return;
                    }

                    int total = task.getResult().size();
                    if (tvStreak != null) tvStreak.setText("0 of " + total + " completed");

                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        String docId      = doc.getId();
                        String pillName   = doc.getString("pillName");
                        String amount     = doc.getString("amount");
                        String notifTime  = doc.getString("notificationTime");
                        String foodOption = doc.getString("foodOption");
                        String days       = doc.getString("days");

                        addMedCard(docId, pillName, amount, notifTime, foodOption, days);
                    }
                }));
    }

    // ── BUILD ONE CARD AND WIRE THE CLICK ─────────────────────────────
    private void addMedCard(String docId, String pillName, String amount,
                            String notifTime, String foodOption, String days) {

        View card = LayoutInflater.from(this)
                .inflate(R.layout.item_med_card, llMedList, false);

        TextView tvName = card.findViewById(R.id.tv_med_name);
        TextView tvInfo = card.findViewById(R.id.tv_med_info);

        tvName.setText(pillName != null ? pillName : "Unknown");

        String time = (notifTime != null && !notifTime.isEmpty()) ? notifTime : "--:--";
        String food = (foodOption != null && !foodOption.isEmpty()) ? foodOption : "";
        tvInfo.setText(time + (food.isEmpty() ? "" : " · " + food));

        // ── Tap → open LogDoseActivity ────────────────────────────────
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, LogDoseActivity.class);
            intent.putExtra("docId",      docId);
            intent.putExtra("pillName",   pillName);
            intent.putExtra("amount",     amount != null ? amount : "");
            intent.putExtra("notifTime",  notifTime != null ? notifTime : "");
            intent.putExtra("foodOption", foodOption != null ? foodOption : "");
            intent.putExtra("days",       days != null ? days : "");
            startActivity(intent);
        });

        // ── Long-press → confirm delete ───────────────────────────────
        card.setOnLongClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Delete Schedule")
                    .setMessage("Remove " + pillName + " from your schedule?")
                    .setPositiveButton("Delete", (dialog, which) ->
                            deleteSchedule(docId, pillName, card))
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        llMedList.addView(card);
    }

    // ── DELETE SCHEDULE FROM FIRESTORE ────────────────────────────────
    private void deleteSchedule(String docId, String pillName, View card) {
        String userId = mAuth.getUid();
        if (userId == null) return;

        db.collection("users")
                .document(userId)
                .collection("schedules")
                .document(docId)
                .delete()
                .addOnSuccessListener(unused -> {
                    androidx.work.WorkManager.getInstance(this)
                            .cancelUniqueWork(pillName + "_reminder");

                    llMedList.removeView(card);

                    if (llMedList.getChildCount() == 0) {
                        android.widget.TextView empty = new android.widget.TextView(this);
                        empty.setText("No medications scheduled yet.\nTap + to add one.");
                        empty.setTextColor(android.graphics.Color.parseColor("#999999"));
                        empty.setTextSize(14f);
                        empty.setPadding(4, 16, 4, 16);
                        llMedList.addView(empty);

                        TextView tvStreak = findViewById(R.id.tv_streak);
                        if (tvStreak != null) tvStreak.setText("0 of 0 completed");
                    }

                    Toast.makeText(this, pillName + " removed", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Delete failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    // ── LOGIC METHODS ──────────────────────────────────────────────────

    public void saveMedication(Medication med) {
        String userId = mAuth.getUid();
        if (userId == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("name", med.getName());
        data.put("dosage", med.getDosage());
        db.collection("users").document(userId)
                .collection("medications").document(med.getId()).set(data)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful())
                        Log.e("Firestore", "Save failed", task.getException());
                });
    }

    public int calculateStreak(List<DoseLog> logs) {
        if (logs == null || logs.isEmpty()) return 0;
        int streak = 0;
        Collections.sort(logs, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        for (DoseLog log : logs) {
            if ("Taken".equals(log.getStatus())) streak++;
            else break;
        }
        return streak;
    }

    public String matchSymptoms(List<String> symptoms) {
        if (symptoms.contains("Fever") && symptoms.contains("Headache")
                && symptoms.contains("Body pain")) {
            return "Possible Malaria. Please consult a doctor.";
        }
        return "Monitor symptoms and seek care if worsened.";
    }

    public void generatePdf(Context context) {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(300, 600, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        canvas.drawText("MediSync Adherence Report", 80, 50, paint);
        document.finishPage(page);
        try {
            File file = new File(context.getExternalFilesDir(null), "report.pdf");
            document.writeTo(new FileOutputStream(file));
            Toast.makeText(context, "PDF Exported!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
        document.close();
    }

    private void displayAdherenceChart(float pct) {
        PieChart chart = findViewById(R.id.chart);
        ArrayList<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(pct, "Taken"));
        entries.add(new PieEntry(100f - pct, "Missed"));
        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(new int[]{
                android.graphics.Color.parseColor("#2ECC71"),
                android.graphics.Color.parseColor("#EEEEEE")
        });
        set.setDrawValues(false);
        chart.setData(new PieData(set));
        chart.setHoleColor(android.graphics.Color.WHITE);
        chart.setHoleRadius(60f);
        chart.setDescription(null);
        chart.getLegend().setEnabled(false);
        chart.invalidate();
    }

    public void saveLog(DoseLog log) {
        String userId = mAuth.getUid();
        if (userId == null) return;
        db.collection("users").document(userId)
                .collection("logs").add(log)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful())
                        Log.e("Firestore", "Log failed", task.getException());
                });
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    "MED_CHANNEL", "Medication",
                    android.app.NotificationManager.IMPORTANCE_HIGH);
            getSystemService(android.app.NotificationManager.class)
                    .createNotificationChannel(channel);
        }
    }
}