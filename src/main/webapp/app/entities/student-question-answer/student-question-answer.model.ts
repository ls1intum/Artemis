import { Moment } from 'moment';
import { BaseEntity } from 'app/shared';
import { StudentQuestion } from 'app/entities/student-question';
import { User } from 'app/core';

export class StudentQuestionAnswer implements BaseEntity {
    public id: number;
    public answerText: string;
    public answerDate: Moment;
    public verified = false; // default value
    public author: User;
    public quizQuestion: StudentQuestion;

    constructor() {
    }
}
