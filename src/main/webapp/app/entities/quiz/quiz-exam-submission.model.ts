import { SubmissionExerciseType } from 'app/entities/submission.model';
import { AbstractQuizSubmission } from 'app/entities/quiz/abstract-quiz-exam-submission.model';
import { StudentExam } from 'app/entities/student-exam.model';

export class QuizExamSubmission extends AbstractQuizSubmission {
    public studentExam?: StudentExam;
    constructor() {
        super(SubmissionExerciseType.QUIZ_EXAM);
        this.isSynced = true;
        this.submitted = false;
    }
}
