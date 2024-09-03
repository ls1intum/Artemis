import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam/exam.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class ExamUser implements BaseEntity {
    public id?: number;
    public actualRoom?: string;
    public actualSeat?: string;
    public plannedRoom?: string;
    public plannedSeat?: string;
    public signingImagePath?: string;
    public studentImagePath?: string;
    public didCheckImage = false; // default value
    public didCheckName = false; // default value
    public didCheckLogin = false; // default value
    public didCheckRegistrationNumber = false; // default value
    public user?: User;
    public exam?: Exam;

    // helper attributes
    public didExamUserAttendExam?: boolean;

    constructor() {
        this.didExamUserAttendExam = false;
    }
}
