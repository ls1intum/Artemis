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

    @Column(name = "example_solution")
    // @Lob
    private String exampleSolution;

    @Column(name = "filePattern")
    private String filePattern;

    public String getFilePattern() {
        return filePattern;
    }

    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }

    public String getExampleSolution() {
        return exampleSolution;
    }

    public void setExampleSolution(String exampleSolution) {
        this.exampleSolution = exampleSolution;
    }

    /**
     * set all sensitive information to null, so no info with respect to the solution gets leaked to students through json
     */
    @Override
    public void filterSensitiveInformation() {
        if (!isExampleSolutionPublished()) {
            setExampleSolution(null);
        }
        super.filterSensitiveInformation();
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
