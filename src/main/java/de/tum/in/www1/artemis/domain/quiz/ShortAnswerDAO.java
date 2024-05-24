package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ShortAnswerDAO implements Serializable {

    private List<ShortAnswerSpot> spots = new ArrayList<>();

    private List<ShortAnswerSolution> solutions = new ArrayList<>();

    private List<ShortAnswerMapping> correctMappings = new ArrayList<>();

    public List<ShortAnswerSpot> getSpots() {
        return spots;
    }

    public void setSpots(List<ShortAnswerSpot> spots) {
        this.spots = spots;
    }

    public List<ShortAnswerSolution> getSolutions() {
        return solutions;
    }

    public void setSolutions(List<ShortAnswerSolution> solutions) {
        this.solutions = solutions;
    }

    public List<ShortAnswerMapping> getCorrectMappings() {
        return correctMappings;
    }

    public void setCorrectMappings(List<ShortAnswerMapping> correctMappings) {
        this.correctMappings = correctMappings;
    }

    public ShortAnswerDAO addSolution(ShortAnswerSolution shortAnswerSolution) {
        this.solutions.add(shortAnswerSolution);
        return this;
    }

    public ShortAnswerDAO removeSolution(ShortAnswerSolution shortAnswerSolution) {
        this.solutions.remove(shortAnswerSolution);
        return this;
    }

    public ShortAnswerDAO addCorrectMapping(ShortAnswerMapping shortAnswerMapping) {
        this.correctMappings.add(shortAnswerMapping);
        return this;
    }

    public ShortAnswerDAO removeCorrectMapping(ShortAnswerMapping shortAnswerMapping) {
        this.correctMappings.remove(shortAnswerMapping);
        return this;
    }
}
