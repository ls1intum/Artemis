import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { Language } from 'app/core/shared/entities/course.model';

export class TextSubmission extends Submission {
    public text?: string;
    public blocks?: TextBlock[];
    public language?: Language;

    constructor() {
        super(SubmissionExerciseType.TEXT);
    }
}
