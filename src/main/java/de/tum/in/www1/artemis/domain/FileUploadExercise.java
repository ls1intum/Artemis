package de.tum.in.www1.artemis.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Lob;

/**
 * A FileUploadExercise.
 */
@Entity
@DiscriminatorValue(value = "F")
public class FileUploadExercise extends Exercise implements Serializable {

    @Column(name = "sample_solution")
    @Lob
    private String sampleSolution;

    @Column(name = "filePattern")
    private String filePattern;

    public String getFilePattern() {
        return filePattern;
    }

    public FileUploadExercise filePattern(String filePattern) {
        this.filePattern = filePattern;
        return this;
    }

    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }

    public String getSampleSolution() {
        return sampleSolution;
    }

    public FileUploadExercise sampleSolution(String sampleSolution) {
        this.sampleSolution = sampleSolution;
        return this;
    }

    public void setSampleSolution(String sampleSolution) {
        this.sampleSolution = sampleSolution;
    }

    @Override
    public String toString() {
        return "FileUploadExercise{" + "id=" + getId() + "}";
    }
}
