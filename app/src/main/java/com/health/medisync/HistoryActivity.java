package com.health.medisync;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryActivity extends AppCompatActivity {

    private static final String TAG = "HistoryActivity";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private LinearLayout llHistoryList;

    // Holds all log entries for PDF export
    private final List<LogEntry> allLogs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        llHistoryList = findViewById(R.id.ll_history_list);

        // ── Back ───────────────────────────────────────────────────────
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // ── Download icon → same as Export PDF ────────────────────────
        findViewById(R.id.btn_download).setOnClickListener(v -> exportPdf());

        // ── Export PDF button ──────────────────────────────────────────
        findViewById(R.id.btn_export_pdf).setOnClickListener(v -> exportPdf());

        // ── Load logs from Firestore ───────────────────────────────────
        String userId = mAuth.getUid();
        if (userId == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loadLogs(userId);
    }

    // ── LOAD LOGS ──────────────────────────────────────────────────────
    private void loadLogs(String userId) {
        db.collection("users")
                .document(userId)
                .collection("logs")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> runOnUiThread(() -> {

                    if (isFinishing() || isDestroyed()) return;

                    if (!task.isSuccessful() || task.getResult() == null) {
                        Log.e(TAG, "Failed to load logs", task.getException());
                        Toast.makeText(this, "Could not load history", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (task.getResult().isEmpty()) {
                        TextView empty = new TextView(this);
                        empty.setText("No dose history yet.");
                        empty.setTextColor(android.graphics.Color.parseColor("#999999"));
                        empty.setTextSize(14f);
                        empty.setPadding(0, 32, 0, 0);
                        llHistoryList.addView(empty);
                        return;
                    }

                    // ── Group logs by date label ───────────────────────
                    // LinkedHashMap preserves insertion order (newest first)
                    Map<String, List<LogEntry>> grouped = new LinkedHashMap<>();
                    long todayStart = getTodayStartMillis();

                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        String pillName  = doc.getString("pillName");
                        String status    = doc.getString("status");
                        Long   timestamp = doc.getLong("timestamp");

                        if (pillName == null || status == null || timestamp == null) continue;

                        String dateLabel = formatDateLabel(timestamp, todayStart);
                        String timeLabel = formatTime(timestamp);

                        LogEntry entry = new LogEntry(pillName, status, timeLabel, timestamp);
                        allLogs.add(entry);

                        if (!grouped.containsKey(dateLabel)) {
                            grouped.put(dateLabel, new ArrayList<>());
                        }
                        grouped.get(dateLabel).add(entry);
                    }

                    // ── Render groups ──────────────────────────────────
                    for (Map.Entry<String, List<LogEntry>> group : grouped.entrySet()) {
                        addDateHeader(group.getKey());
                        for (LogEntry entry : group.getValue()) {
                            addLogRow(entry);
                        }
                    }
                }));
    }

    // ── ADD DATE HEADER ────────────────────────────────────────────────
    private void addDateHeader(String label) {
        View header = LayoutInflater.from(this)
                .inflate(R.layout.item_history_date_header, llHistoryList, false);
        ((TextView) header.findViewById(R.id.tv_date_header)).setText(label);
        llHistoryList.addView(header);
    }

    // ── ADD LOG ROW ────────────────────────────────────────────────────
    private void addLogRow(LogEntry entry) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_history_row, llHistoryList, false);

        ((TextView) row.findViewById(R.id.tv_pill_name)).setText(entry.pillName);
        ((TextView) row.findViewById(R.id.tv_pill_time)).setText(entry.timeLabel);

        TextView badge = row.findViewById(R.id.tv_status_badge);
        badge.setText(entry.status);

        // Colour badge by status
        switch (entry.status) {
            case "Taken":
                badge.setBackgroundResource(R.drawable.btn_green_rounded);
                break;
            case "Skipped":
                badge.setBackgroundResource(R.drawable.btn_orange_rounded);
                break;
            default: // Delayed or other
                badge.setBackgroundColor(android.graphics.Color.parseColor("#BBBBBB"));
                break;
        }

        llHistoryList.addView(row);
    }

    // ── EXPORT PDF ─────────────────────────────────────────────────────
    private void exportPdf() {
        if (allLogs.isEmpty()) {
            Toast.makeText(this, "No history to export", Toast.LENGTH_SHORT).show();
            return;
        }

        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(595, 842, 1).create(); // A4
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        // Title
        paint.setTextSize(20f);
        paint.setFakeBoldText(true);
        canvas.drawText("MediSync — Dose History", 40, 60, paint);

        // Date generated
        paint.setTextSize(11f);
        paint.setFakeBoldText(false);
        paint.setColor(android.graphics.Color.GRAY);
        String generated = "Generated: " + new SimpleDateFormat(
                "EEE, dd MMM yyyy", Locale.getDefault()).format(new Date());
        canvas.drawText(generated, 40, 85, paint);

        // Divider line
        paint.setColor(android.graphics.Color.LTGRAY);
        canvas.drawLine(40, 95, 555, 95, paint);

        // Log rows
        paint.setColor(android.graphics.Color.BLACK);
        int y = 120;
        String currentDate = "";

        for (LogEntry entry : allLogs) {
            String dateLabel = new SimpleDateFormat(
                    "EEE, dd MMM yyyy", Locale.getDefault())
                    .format(new Date(entry.timestamp));

            if (!dateLabel.equals(currentDate)) {
                // Date group header
                paint.setTextSize(12f);
                paint.setFakeBoldText(true);
                paint.setColor(android.graphics.Color.GRAY);
                canvas.drawText(dateLabel, 40, y, paint);
                y += 20;
                currentDate = dateLabel;
            }

            // Row
            paint.setTextSize(13f);
            paint.setFakeBoldText(false);
            paint.setColor(android.graphics.Color.BLACK);
            canvas.drawText(entry.pillName, 40, y, paint);
            canvas.drawText(entry.timeLabel, 260, y, paint);

            // Status in colour
            switch (entry.status) {
                case "Taken":
                    paint.setColor(android.graphics.Color.parseColor("#2ECC71"));
                    break;
                case "Skipped":
                    paint.setColor(android.graphics.Color.parseColor("#F39C12"));
                    break;
                default:
                    paint.setColor(android.graphics.Color.GRAY);
            }
            canvas.drawText(entry.status, 420, y, paint);
            paint.setColor(android.graphics.Color.BLACK);

            y += 28;
            if (y > 800) break; // Simple single-page guard
        }

        document.finishPage(page);

        try {
            File file = new File(getExternalFilesDir(null), "MediSync_History.pdf");
            document.writeTo(new FileOutputStream(file));
            Toast.makeText(this, "PDF saved to: " + file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e(TAG, "PDF export failed", e);
            Toast.makeText(this, "Export failed: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }

        document.close();
    }

    // ── DATE FORMATTING HELPERS ────────────────────────────────────────

    private String formatDateLabel(long timestamp, long todayStart) {
        long yesterdayStart = todayStart - 86_400_000L;

        if (timestamp >= todayStart) {
            // "Today • Tue, Apr 23"
            String dayPart = new SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                    .format(new Date(timestamp));
            return "Today • " + dayPart;
        } else if (timestamp >= yesterdayStart) {
            String dayPart = new SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                    .format(new Date(timestamp));
            return "Yesterday • " + dayPart;
        } else {
            return new SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                    .format(new Date(timestamp));
        }
    }

    private String formatTime(long timestamp) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(new Date(timestamp));
    }

    private long getTodayStartMillis() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // ── INNER MODEL CLASS ──────────────────────────────────────────────
    private static class LogEntry {
        String pillName;
        String status;
        String timeLabel;
        long   timestamp;

        LogEntry(String pillName, String status, String timeLabel, long timestamp) {
            this.pillName  = pillName;
            this.status    = status;
            this.timeLabel = timeLabel;
            this.timestamp = timestamp;
        }
    }
}