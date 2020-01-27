import { Moment } from 'moment';
import { BaseEntity } from 'app/shared';
import { Lecture } from 'app/entities/lecture';
import { Exercise } from 'app/entities/exercise';
import { User } from 'app/core/user/user.model';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer';

export class StudentQuestion implements BaseEntity {
    public id: number;
    public questionText: string | null;
    public creationDate: Moment | null;
    public visibleForStudents = true; // default value
    public answers: StudentQuestionAnswer[];
    public author: User;
    public exercise: Exercise;
    public lecture: Lecture;

    constructor() {}
}
