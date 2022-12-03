import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Course } from 'app/entities/course.model';
import { Posting } from 'app/entities/metis/posting.model';
import { CourseWideContext, DisplayPriority } from 'app/shared/metis/metis.util';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

export class Post extends Posting {
    public title?: string;
    public visibleForStudents?: boolean;
    public answers?: AnswerPost[];
    public tags?: string[];
    public exercise?: Exercise;
    public lecture?: Lecture;
    public course?: Course;
    public plagiarismCase?: PlagiarismCase;
    public conversation?: Conversation;
    public courseWideContext?: CourseWideContext;
    public displayPriority?: DisplayPriority;
    public resolved?: boolean;

    constructor() {
        super();
        // set default values
        this.visibleForStudents = true;
        this.displayPriority = DisplayPriority.NONE;
    }
}
