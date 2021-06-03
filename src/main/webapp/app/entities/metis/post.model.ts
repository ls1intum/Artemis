import { Moment } from 'moment';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';
import { User } from 'app/core/user/user.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Course } from 'app/entities/course.model';
import { PostTag } from 'app/entities/metis/post-tag.model';

export enum CourseWideContext {
    TECH_SUPPORT = 'TECH_SUPPORT',
    ORGANIZATION = 'ORGANIZATION',
    RANDOM = 'RANDOM',
}

export class Post implements BaseEntity {
    public id?: number;
    public author?: User;
    public creationDate?: Moment;
    public content?: string;
    public tokenizedContent?: string;
    public title?: string;
    public visibleForStudents?: boolean;
    public votes?: number;
    public answers?: AnswerPost[];
    public tags: PostTag[];
    public exercise?: Exercise;
    public lecture?: Lecture;
    public course?: Course;
    public courseWideContext?: CourseWideContext;

    constructor() {
        this.visibleForStudents = true; // default value
        this.votes = 0; // default value
    }
}
