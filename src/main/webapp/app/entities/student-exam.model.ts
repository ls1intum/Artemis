import dayjs from 'dayjs';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { Exercise } from 'app/entities/exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { ExamSession } from 'app/entities/exam-session.model';

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

    // helper attribute
    public ended?: boolean;

    constructor() {
        // helper attribute (calculated by the server at the time of the last request)
        this.ended = false;
    }
}
