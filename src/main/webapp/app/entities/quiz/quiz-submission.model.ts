import { SubmissionExerciseType } from 'app/entities/submission.model';
import { AbstractQuizSubmission } from 'app/entities/quiz/abstract-quiz-exam-submission.model';

export class QuizSubmission extends AbstractQuizSubmission {
    constructor() {
        super(SubmissionExerciseType.QUIZ);
    }
}
