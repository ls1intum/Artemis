package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

public class ProgrammingExercisePageDTO {

    private List<ProgrammingExercise> exercisesOnPage;

    private int numberOfPages;

    public ProgrammingExercisePageDTO() {
    }

    public ProgrammingExercisePageDTO(List<ProgrammingExercise> exercisesOnPage, int numberOfPages) {
        this.exercisesOnPage = exercisesOnPage;
        this.numberOfPages = numberOfPages;
    }

    public List<ProgrammingExercise> getExercisesOnPage() {
        return exercisesOnPage;
    }

    public void setExercisesOnPage(List<ProgrammingExercise> exercisesOnPage) {
        this.exercisesOnPage = exercisesOnPage;
    }

    public int getNumberOfPages() {
        return numberOfPages;
    }

    public void setNumberOfPages(int numberOfPages) {
        this.numberOfPages = numberOfPages;
    }
}
