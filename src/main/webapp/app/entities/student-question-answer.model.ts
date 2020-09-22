import { Moment } from 'moment';
import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import { StudentQuestion } from 'app/entities/student-question.model';

export class StudentQuestionAnswer implements BaseEntity {
    public id?: number;
    public answerText?: string;
    public answerDate?: Moment;
    public verified?: boolean;
    public tutorApproved?: boolean;
    public author?: User;
    public question?: StudentQuestion;

    constructor() {
        this.verified = false; // default value
        this.tutorApproved = false; // default value
    }
}
