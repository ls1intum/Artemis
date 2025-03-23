import { SubmittedAnswer } from 'app/entities/quiz/submitted-answer.model';
import { Submission, SubmissionExerciseType } from 'app/exercise/entities/submission.model';

export abstract class AbstractQuizSubmission extends Submission {
    public scoreInPoints?: number;
    public submittedAnswers?: SubmittedAnswer[];

    protected constructor(type: SubmissionExerciseType) {
        super(type);
    }
}
