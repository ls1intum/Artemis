import dayjs from 'dayjs/esm';
import { User } from 'app/account/user/user.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { BaseEntity } from 'app/foundation/model/base-entity';
import { ExamSession } from 'app/exam/shared/entities/exam-session.model';

export class StudentExam implements BaseEntity {
    public id?: number;
    /**
     * The individual working time per student in seconds
     * The default working time of an exam is stored in exam.workingTime
     */
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

    // helper attribute
    public ended?: boolean;
    public numberOfExamSessions = 0; // default value

    constructor() {
        // helper attribute (calculated by the server at the time of the last request)
        this.ended = false;
    }

    /**
     * Returns a new StudentExam holding the same values, with every nested value carried over as it is.
     *
     * This is deliberately not a {@link deepClone}: it exists so a consumer bound to a `studentExam` signal is
     * notified after the object was updated in place, and the exercises and submissions a student is working on must
     * stay the same instances. A signal's `equal` option cannot do this, because Angular compares a property binding
     * with `Object.is` before it reaches a child's `input()`, so the same reference never arrives at the child.
     *
     * Keep this in sync with the fields above.
     */
    static withSameValues(studentExam: StudentExam): StudentExam {
        const rebuilt = new StudentExam();
        rebuilt.id = studentExam.id;
        rebuilt.workingTime = studentExam.workingTime;
        rebuilt.submitted = studentExam.submitted;
        rebuilt.started = studentExam.started;
        rebuilt.testRun = studentExam.testRun;
        rebuilt.submissionDate = studentExam.submissionDate;
        rebuilt.user = studentExam.user;
        rebuilt.exam = studentExam.exam;
        rebuilt.exercises = studentExam.exercises;
        rebuilt.examSessions = studentExam.examSessions;
        rebuilt.startedDate = studentExam.startedDate;
        rebuilt.ended = studentExam.ended;
        rebuilt.numberOfExamSessions = studentExam.numberOfExamSessions;
        return rebuilt;
    }
}
