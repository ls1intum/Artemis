import { SubmittedAnswer } from 'app/entities/quiz/submitted-answer.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';

export class QuizSubmission extends Submission {
    public scoreInPoints: number;
    public submittedAnswers: SubmittedAnswer[];

    // helper attributes
    public adjustedSubmissionDate: Date;

    constructor() {
        super(SubmissionExerciseType.QUIZ);
    }
}
