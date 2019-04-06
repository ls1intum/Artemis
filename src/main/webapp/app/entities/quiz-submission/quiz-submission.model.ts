import { Submission, SubmissionExerciseType } from '../submission';
import { SubmittedAnswer } from '../submitted-answer';

export class QuizSubmission extends Submission {
    public scoreInPoints: number;
    public submittedAnswers: SubmittedAnswer[];

    // helper attributes
    public adjustedSubmissionDate: Date;

    constructor() {
        super(SubmissionExerciseType.QUIZ);
    }
}
