package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.domain.enumeration.ExerciseType.TEXT;

import java.util.List;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;

/**
 * A TextExercise.
 */
@Entity
@DiscriminatorValue(value = "T")
@SecondaryTable(name = "text_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextExercise extends Exercise {

    @Column(name = "example_solution")
    // @Lob
    private String exampleSolution;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<TextCluster> clusters;

    @ManyToOne
    @JoinColumn(table = "text_exercise_details")
    @JsonIgnore
    private TextAssessmentKnowledge knowledge;

    public String getExampleSolution() {
        return exampleSolution;
    }

    public void setExampleSolution(String exampleSolution) {
        this.exampleSolution = exampleSolution;
    }

    public boolean isAutomaticAssessmentEnabled() {
        return getAssessmentType() == AssessmentType.SEMI_AUTOMATIC;
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
    public ExerciseType getExerciseType() {
        return TEXT;
    }

    @Override
    public String toString() {
        return "TextExercise{" + "id=" + getId() + ", exampleSolution='" + getExampleSolution() + "'" + "}";
    }

    public TextAssessmentKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(TextAssessmentKnowledge knowledge) {
        this.knowledge = knowledge;
    }

}
