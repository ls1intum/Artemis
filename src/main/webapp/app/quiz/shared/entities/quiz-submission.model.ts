import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import type { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';

export class QuizSubmission extends Submission {
    public scoreInPoints?: number;
    public submittedAnswers?: SubmittedAnswer[];

    constructor() {
        super(SubmissionExerciseType.QUIZ);
    }
}
