package de.tum.in.www1.artemis.service.compass.assessment;

public class ModelElementAssessment {
    //TODO enum for type ? change credits name?
    private String commment;
    private double credits;
    private String id; //TODO rename into jsonElementID?
    private String type;

    public ModelElementAssessment() {
    }

    public ModelElementAssessment(String commment, double credits, String id, String type) {
        this.commment = commment;
        this.credits = credits;
        this.id = id;
        this.type = type;
    }

    public String getCommment() {
        return commment;
    }

    public double getCredits() {
        return credits;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }
}
