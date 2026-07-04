import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { AbstractQuizSubmission } from 'app/quiz/shared/entities/abstract-quiz-exam-submission.model';

export class QuizSubmission extends AbstractQuizSubmission {
    public quizBatch?: number;

    constructor() {
        super(SubmissionExerciseType.QUIZ);
    }
}
