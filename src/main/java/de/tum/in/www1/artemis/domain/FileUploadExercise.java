package de.tum.in.www1.artemis.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * A FileUploadExercise.
 */
@Entity
@Table(name = "file_upload_exercise")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class FileUploadExercise implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_pattern")
    private String filePattern;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

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
            ", filePattern='" + getFilePattern() + "'" +
            "}";
    }
}
