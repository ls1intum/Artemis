import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { ExamSession } from 'app/entities/exam-session.model';
import { ExamActivity } from 'app/entities/exam-user-activity.model';
import { QuizExamSubmission } from 'app/entities/quiz/quiz-exam-submission.model';
import { Exercise } from 'app/entities/exercise.model';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';

export class StudentExam implements BaseEntity {
    public id?: number;
    public workingTime?: number;
    public submitted?: boolean;
    public started?: boolean;
    public testRun?: boolean;
    public submissionDate?: dayjs.Dayjs;
    public user?: User;
    public exam?: Exam;
    public exercises?: Exercise[];
    public examSessions?: ExamSession[];
    public startedDate?: dayjs.Dayjs;
    public examActivity?: ExamActivity;
    public quizQuestions?: QuizQuestion[];

    // helper attribute
    public ended?: boolean;
    public numberOfExamSessions = 0; // default value
    public quizQuestionTotalPoints?: number = 0;
    public quizExamSubmission?: QuizExamSubmission;

    constructor() {
        // helper attribute (calculated by the server at the time of the last request)
        this.ended = false;
    }
}
