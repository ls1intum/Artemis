package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.domain.enumeration.ExerciseType.FILE_UPLOAD;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;

/**
 * A FileUploadExercise.
 */
@Entity
@DiscriminatorValue(value = "F")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FileUploadExercise extends Exercise {

    @Column(name = "sample_solution")
    @Lob
    private String sampleSolution;

    @Column(name = "filePattern")
    private String filePattern;

    public String getFilePattern() {
        return filePattern;
    }

    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }

    public String getSampleSolution() {
        return sampleSolution;
    }

    public void setSampleSolution(String sampleSolution) {
        this.sampleSolution = sampleSolution;
    }

    @Override
    public String toString() {
        return "FileUploadExercise{" + "id=" + getId() + "}";
    }

    @Override
    public ExerciseType getExerciseType() {
        return FILE_UPLOAD;
    }
}
