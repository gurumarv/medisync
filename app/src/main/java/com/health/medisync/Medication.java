package com.health.medisync;

public class Medication {
    private String id;
    private String name;
    private String dosage;
    private String frequency;
    private String foodRelation;
    private long startDate;

    public Medication() {}

    public Medication(String id, String name, String dosage,
                      String frequency, String foodRelation, long startDate) {
        this.id = id;
        this.name = name;
        this.dosage = dosage;
        this.frequency = frequency;
        this.foodRelation = foodRelation;
        this.startDate = startDate;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDosage() { return dosage; }
    public String getFrequency() { return frequency; }
    public String getFoodRelation() { return foodRelation; }
    public long getStartDate() { return startDate; }

}
