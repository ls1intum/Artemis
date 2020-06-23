package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;

/**
 * A TextExercise.
 */
@Entity
@DiscriminatorValue(value = "T")
public class TextExercise extends Exercise implements Serializable {

    @Column(name = "sample_solution")
    @Lob
    private String sampleSolution;

    @OneToMany(mappedBy = "exercise")
    @JsonIgnore
    private List<TextCluster> clusters;

    public String getSampleSolution() {
        return sampleSolution;
    }

    public TextExercise sampleSolution(String sampleSolution) {
        this.sampleSolution = sampleSolution;
        return this;
    }

    public void setSampleSolution(String sampleSolution) {
        this.sampleSolution = sampleSolution;
    }

    public boolean isAutomaticAssessmentEnabled() {
        return getAssessmentType() == AssessmentType.SEMI_AUTOMATIC;
    }

    /**
     * set all sensitive information to null, so no info with respect to the solution gets leaked to students through json
     */
    @Override
    public void filterSensitiveInformation() {
        setSampleSolution(null);
        super.filterSensitiveInformation();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TextExercise textExercise = (TextExercise) o;
        if (textExercise.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), textExercise.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "TextExercise{" + "id=" + getId() + ", sampleSolution='" + getSampleSolution() + "'" + "}";
    }

    /**
     * Columns for which we allow a pageable search using the {@link de.tum.in.www1.artemis.service.TextExerciseService#getAllOnPageWithSize(PageableSearchDTO, User)}}
     * method. This ensures, that we can't search in columns that don't exist, or we do not want to be searchable.
     */
    public enum TextExerciseSearchColumn {

        ID("id"), TITLE("title"), COURSE_TITLE("course.title");

        private String mappedColumnName;

        TextExerciseSearchColumn(String mappedColumnName) {
            this.mappedColumnName = mappedColumnName;
        }

        public String getMappedColumnName() {
            return mappedColumnName;
        }
    }
}
