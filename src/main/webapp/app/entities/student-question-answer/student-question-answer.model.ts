import { Moment } from 'moment';
import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import { StudentQuestion } from 'app/entities/student-question/student-question.model';

export class StudentQuestionAnswer implements BaseEntity {
    public id: number;
    public answerText: string | null;
    public answerDate: Moment | null;
    public verified = false; // default value
    public author: User;
    public question: StudentQuestion;

    constructor() {}
}
