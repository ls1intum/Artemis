package de.tum.in.www1.artemis.web.rest.dto;

import java.util.Set;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmissionElement;

public class PlagiarismCaseDTO {

    private Exercise exercise;

    private Set<PlagiarismComparison<? extends PlagiarismSubmissionElement>> comparisons;

    public PlagiarismCaseDTO(Exercise exercise, Set<PlagiarismComparison<? extends PlagiarismSubmissionElement>> comparisons) {
        this.exercise = exercise;
        this.comparisons = comparisons;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Set<PlagiarismComparison<? extends PlagiarismSubmissionElement>> getComparisons() {
        return comparisons;
    }

    public void setComparisons(Set<PlagiarismComparison<? extends PlagiarismSubmissionElement>> comparisons) {
        this.comparisons = comparisons;
    }
}
