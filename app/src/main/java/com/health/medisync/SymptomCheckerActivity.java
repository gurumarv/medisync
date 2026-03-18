package com.health.medisync;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class SymptomCheckerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_symptom_checker);

        // ── Back Button ────────────────────────────────────────────────
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // ── Check Button ───────────────────────────────────────────────
        findViewById(R.id.btn_check).setOnClickListener(v -> {

            // ── IDs match activity_symptom_checker.xml exactly ─────────
            int[] checkboxIds = {
                    R.id.cb_fever,
                    R.id.cb_nausea,
                    R.id.cb_headache,
                    R.id.cb_fatigue,
                    R.id.cb_sore_throat,   // was cb_dizziness — fixed
                    R.id.cb_muscle_ache    // was cb_body_pain  — fixed
            };
            String[] labels = {
                    "Fever", "Nausea", "Headache", "Fatigue", "Sore Throat", "Muscle Ache"
            };

            List<String> selected = new ArrayList<>();
            for (int i = 0; i < checkboxIds.length; i++) {
                CheckBox cb = findViewById(checkboxIds[i]);
                if (cb != null && cb.isChecked()) {
                    selected.add(labels[i]);
                }
            }

            if (selected.isEmpty()) {
                Toast.makeText(this, "Please select at least one symptom",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            String result = matchSymptoms(selected);
            TextView tvResult = findViewById(R.id.tv_symptom_result);
            if (tvResult != null) {
                tvResult.setVisibility(View.VISIBLE);
                tvResult.setText(result);
            }
        });
    }

    // ── SYMPTOM MATCHING LOGIC ─────────────────────────────────────────
    private String matchSymptoms(List<String> symptoms) {
        boolean fever      = symptoms.contains("Fever");
        boolean headache   = symptoms.contains("Headache");
        boolean muscleAche = symptoms.contains("Muscle Ache");
        boolean nausea     = symptoms.contains("Nausea");
        boolean soreThroat = symptoms.contains("Sore Throat");
        boolean fatigue    = symptoms.contains("Fatigue");

        if (fever && muscleAche && headache) {
            return "⚠️ Possible Malaria or Flu. Please consult a doctor immediately.";
        }
        if (soreThroat && fever) {
            return "⚠️ Possible throat infection or Strep. Consider seeing a doctor.";
        }
        if (nausea && fatigue) {
            return "⚠️ Possible dehydration or viral infection. Rest and drink fluids.";
        }
        if (fatigue && headache) {
            return "⚠️ Possible stress or dehydration. Drink water and rest.";
        }
        if (fever && nausea) {
            return "⚠️ Possible infection. Monitor temperature and seek care if it worsens.";
        }
        return "ℹ️ Symptoms noted. Monitor your condition and consult a doctor if they persist.";
    }
}