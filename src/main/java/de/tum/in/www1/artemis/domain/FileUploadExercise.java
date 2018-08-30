package de.tum.in.www1.artemis.domain;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.io.Serializable;
import java.util.Objects;

/**
 * A FileUploadExercise.
 */

@Entity
@DiscriminatorValue(value="F")
public class FileUploadExercise extends Exercise implements Serializable {

    @Column(name = "filePattern")
    private String filePattern;

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FileUploadExercise fileUploadExercise = (FileUploadExercise) o;
        if (fileUploadExercise.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), fileUploadExercise.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "FileUploadExercise{" +
            "id=" + getId() +
            "}";
    }
}
