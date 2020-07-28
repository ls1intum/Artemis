import { TextBlock } from 'app/entities/text-block.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';

export class TextSubmission extends Submission {
    public text?: string;
    blocks?: TextBlock[];
    // needed for tutor assessment tracking of text exercises with athene
    public atheneTextAssessmentTrackingToken: string | null;

    constructor() {
        super(SubmissionExerciseType.TEXT);
    }
}
