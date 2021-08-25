import { SubmissionExerciseType } from 'app/entities/submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';

export class TransformationModelingSubmission extends ModelingSubmission {
    constructor() {
        super();
        this.submissionExerciseType = SubmissionExerciseType.TRANSFORMATION;
    }
}
