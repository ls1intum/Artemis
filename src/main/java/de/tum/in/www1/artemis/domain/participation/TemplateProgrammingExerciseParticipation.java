package de.tum.in.www1.artemis.domain.participation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;

@Entity
@DiscriminatorValue(value = "TPEP")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TemplateProgrammingExerciseParticipation extends AbstractBaseProgrammingExerciseParticipation {

    @OneToOne(mappedBy = "templateParticipation")
    @JsonIgnoreProperties("templateParticipation")
    private ProgrammingExercise programmingExercise;

    @Override
    public ProgrammingExercise getProgrammingExercise() {
        return programmingExercise;
    }

    @Override
    public void setProgrammingExercise(ProgrammingExercise programmingExercise) {
        this.programmingExercise = programmingExercise;
    }

    @Override
    public String getType() {
        return "template";
    }

    @Override
    public Participation copyParticipationId() {
        var participation = new TemplateProgrammingExerciseParticipation();
        participation.setId(getId());
        return participation;
    }
}
