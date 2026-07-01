import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Language } from 'app/course/shared/entities/course.model';
import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission-exercise-type.model';

export class TextSubmission extends Submission {
    public text?: string;
    public blocks?: TextBlock[];
    public language?: Language;

    constructor() {
        super(SubmissionExerciseType.TEXT);
    }
}
