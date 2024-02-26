import { Operation } from 'fast-json-patch';
import { SubmissionExerciseType } from 'app/entities/submission.model';
import { SubmissionPatch } from 'app/entities/submission-patch.model';

export class ModelingSubmissionPatch extends SubmissionPatch {
    constructor(patch: Operation[]) {
        super(patch, SubmissionExerciseType.MODELING);
    }
}
