package de.tum.in.www1.artemis.domain.participation;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

@Entity
@DiscriminatorValue(value = "SPEP")
public class SolutionProgrammingExerciseParticipation extends AbstractBaseProgrammingExerciseParticipation {

    @OneToOne(mappedBy = "solutionParticipation")
    @JsonIgnoreProperties("solutionParticipation")
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
    public Participation copyParticipationId() {
        var participation = new SolutionProgrammingExerciseParticipation();
        participation.setId(getId());
        return participation;
    }
}
