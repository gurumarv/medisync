package com.health.medisync;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText etEmail, etPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // ── 1. Initialize Firebase Auth ────────────────────────────────
        mAuth = FirebaseAuth.getInstance();

        // ── 2. Bind Views ──────────────────────────────────────────────
        etEmail   = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        Button btnLogin     = findViewById(R.id.btn_login);
        TextView tvSignUp   = findViewById(R.id.tv_signup_link);
        TextView tvForgot   = findViewById(R.id.tv_forgot);

        // ── 3. Login Button ────────────────────────────────────────────
        btnLogin.setOnClickListener(v -> {
            String email    = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            if (!email.isEmpty() && !password.isEmpty()) {
                loginUser(email, password);
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            }
        });

        // ── 4. Sign Up link ────────────────────────────────────────────
        tvSignUp.setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));

        // ── 5. Forgot Password ─────────────────────────────────────────
        tvForgot.setOnClickListener(v -> showForgotPasswordDialog());
    }

    // ── LOGIN ──────────────────────────────────────────────────────────
    private void loginUser(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    } else {
                        String errorMsg = (task.getException() != null)
                                ? task.getException().getMessage()
                                : "Authentication failed";
                        Toast.makeText(this, "Error: " + errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ── FORGOT PASSWORD DIALOG ─────────────────────────────────────────
    private void showForgotPasswordDialog() {
        // Build an input dialog with a single email field
        EditText inputEmail = new EditText(this);
        inputEmail.setHint("Enter your email");
        inputEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        inputEmail.setPadding(48, 24, 48, 24);

        // Pre-fill with whatever is already in the login email field
        String existing = etEmail.getText().toString().trim();
        if (!existing.isEmpty()) inputEmail.setText(existing);

        new AlertDialog.Builder(this)
                .setTitle("Reset Password")
                .setMessage("We'll send a reset link to your email.")
                .setView(inputEmail)
                .setPositiveButton("Send", (dialog, which) -> {
                    String email = inputEmail.getText().toString().trim();
                    if (TextUtils.isEmpty(email)) {
                        Toast.makeText(this, "Please enter your email",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendPasswordReset(email);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── SEND RESET EMAIL VIA FIREBASE ─────────────────────────────────
    private void sendPasswordReset(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Reset link sent to " + email,
                                Toast.LENGTH_LONG).show();
                    } else {
                        String error = (task.getException() != null)
                                ? task.getException().getMessage()
                                : "Failed to send reset email";
                        Toast.makeText(this, "Error: " + error,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}