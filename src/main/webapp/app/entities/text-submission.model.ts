import { TextBlock } from 'app/entities/text-block.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { Language } from 'app/entities/course.model';

export class TextSubmission extends Submission {
    public text?: string;
    public blocks?: TextBlock[];
    public language?: Language;

    // needed for tutor assessment tracking of text exercises with athene
    public atheneTextAssessmentTrackingToken?: string;

    constructor() {
        super(SubmissionExerciseType.TEXT);
    }
}
