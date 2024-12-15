package de.tum.cit.aet.artemis.iris.service.pyris.event;

import java.util.Optional;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

public class NewResultEvent extends PyrisEvent {

    private final Result result;

    public NewResultEvent(Object source, Result result) {
        super(source);
        if (result == null) {
            throw new IllegalArgumentException("Result cannot be null");
        }
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @Override
    public Optional<User> getUser() {
        if (result.getSubmission().getParticipation() instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
            return studentParticipation.getStudent();
        }
        return Optional.empty();
    }
}
