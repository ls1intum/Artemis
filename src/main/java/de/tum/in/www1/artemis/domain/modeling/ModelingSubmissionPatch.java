package de.tum.in.www1.artemis.domain.modeling;

import de.tum.in.www1.artemis.domain.SubmissionPatch;

public class ModelingSubmissionPatch extends SubmissionPatch {

    @Override
    public String getSubmissionExerciseType() {
        return "modeling";
    }
}
