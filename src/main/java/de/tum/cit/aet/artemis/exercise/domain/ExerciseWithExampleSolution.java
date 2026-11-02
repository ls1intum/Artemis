package de.tum.cit.aet.artemis.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/** Textual example solutions share the exercise's publication policy and sensitive-information filtering. */
@MappedSuperclass
public abstract class ExerciseWithExampleSolution extends Exercise {

    @Column(name = "example_solution")
    private String exampleSolution;

    public String getExampleSolution() {
        return exampleSolution;
    }

    public void setExampleSolution(String exampleSolution) {
        this.exampleSolution = exampleSolution;
    }

    @Override
    public void filterSensitiveInformation() {
        if (!isExampleSolutionPublished()) {
            setExampleSolution(null);
        }
        super.filterSensitiveInformation();
    }
}
