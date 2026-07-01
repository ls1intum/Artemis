import { AbstractQuizSubmission } from 'app/quiz/shared/entities/abstract-quiz-exam-submission.model';
import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission-exercise-type.model';

export class QuizSubmission extends AbstractQuizSubmission {
    constructor() {
        super(SubmissionExerciseType.QUIZ);
    }
}
