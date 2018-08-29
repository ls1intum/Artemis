import { Submission, SubmissionExerciseType } from '../submission';

export class TextSubmission extends Submission {

    public text: string;

    constructor() {
        super(SubmissionExerciseType.TEXT);
    }
}
