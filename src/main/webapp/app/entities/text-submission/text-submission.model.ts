import { TextBlock } from 'app/entities/text-block/text-block.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission/submission.model';

export class TextSubmission extends Submission {
    public text: string;
    blocks?: TextBlock[];

    constructor() {
        super(SubmissionExerciseType.TEXT);
    }
}
