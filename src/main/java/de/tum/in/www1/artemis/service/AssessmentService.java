package de.tum.in.www1.artemis.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

abstract class AssessmentService {
    /**
     * Helper function to calculate the total score of an assessment json. It loops through all assessed model elements
     * and sums the credits up.
     *
     * @param assessmentJson    the assessments as JsonObject
     * @return the total score
     */
    Double calculateTotalScore(JsonObject assessmentJson) {
        double totalScore = 0.0;
        JsonArray assessments = assessmentJson.get("assessments").getAsJsonArray();
        for (JsonElement assessment : assessments) {
            totalScore += assessment.getAsJsonObject().getAsJsonPrimitive("credits").getAsDouble();
        }
        //TODO round this value to max two numbers after the comma
        return totalScore;
    }
}
