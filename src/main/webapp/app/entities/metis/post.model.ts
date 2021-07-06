import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Course } from 'app/entities/course.model';
import { PostTag } from 'app/entities/metis/post-tag.model';
import { Posting } from 'app/entities/metis/posting.model';

export enum CourseWideContext {
    TECH_SUPPORT = 'TECH_SUPPORT',
    ORGANIZATION = 'ORGANIZATION',
    RANDOM = 'RANDOM',
}

export class Post extends Posting {
    public title?: string;
    public visibleForStudents?: boolean;
    public votes?: number;
    public answers?: AnswerPost[];
    public tags?: PostTag[];
    public exercise?: Exercise;
    public lecture?: Lecture;
    public course?: Course;
    public courseWideContext?: CourseWideContext;

    constructor() {
        super();
        this.visibleForStudents = true; // default value
        this.votes = 0; // default value
    }
}
