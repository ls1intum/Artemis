import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';

export abstract class AbstractQuizSubmission extends Submission {
    public scoreInPoints?: number;
    public submittedAnswers?: SubmittedAnswer[];

    protected constructor(type: SubmissionExerciseType) {
        super(type);
    }
}
