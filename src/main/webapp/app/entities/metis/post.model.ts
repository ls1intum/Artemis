import { Moment } from 'moment';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';
import { User } from 'app/core/user/user.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

export class Post implements BaseEntity {
    public id?: number;
    public title?: string;
    public content?: string;
    public tokenizedContent?: string;
    public creationDate?: Moment;
    public visibleForStudents?: boolean;
    public answers?: AnswerPost[];
    public author?: User;
    public exercise?: Exercise;
    public lecture?: Lecture;
    public votes?: number;

    constructor() {
        this.visibleForStudents = true; // default value
        this.votes = 0; // default value
    }
}
