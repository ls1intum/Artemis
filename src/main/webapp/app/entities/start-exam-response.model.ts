import { StudentExam } from './student-exam.model';
import { ExamSession } from './exam-session.model';

export class StartExamResponse {
    public studentExam: StudentExam;
    public examSession: ExamSession;
}
