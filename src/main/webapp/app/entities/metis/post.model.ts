import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Course } from 'app/entities/course.model';
import { Posting } from 'app/entities/metis/posting.model';

export enum CourseWideContext {
    TECH_SUPPORT = 'TECH_SUPPORT',
    ORGANIZATION = 'ORGANIZATION',
    RANDOM = 'RANDOM',
}

export class Post extends Posting {
    public title?: string;
    public visibleForStudents?: boolean;
    public answers?: AnswerPost[];
    public tags?: string[];
    public exercise?: Exercise;
    public lecture?: Lecture;
    public course?: Course;
    public courseWideContext?: CourseWideContext;
    public pinned?: boolean;
    public archived?: boolean;
    // deprecated, will be removed
    public votes?: number;

    constructor() {
        super();
        // set default values
        this.visibleForStudents = true;
        this.votes = 0;
        this.pinned = false;
        this.archived = false;
    }
}
