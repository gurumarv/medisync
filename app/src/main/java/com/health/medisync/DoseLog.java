package com.health.medisync;

public class DoseLog {
    private String medicationId;
    private String status; // Taken, Skipped, Delayed
    private long timestamp;

    public DoseLog() {}

    public DoseLog(String medicationId, String status, long timestamp) {
        this.medicationId = medicationId;
        this.status = status;
        this.timestamp = timestamp;
    }

    // Getters and setters
    // --- GETTERS (Required for calculateAdherence and Firebase) ---

    public String getStatus() {
        return status;
    }

    public String getMedicationId() {
        return medicationId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // --- SETTERS (Required for Firebase to "write" data into the object) ---

    public void setStatus(String status) {
        this.status = status;
    }

    public void setMedicationId(String medicationId) {
        this.medicationId = medicationId;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
