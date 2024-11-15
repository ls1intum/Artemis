import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Posting } from 'app/entities/metis/posting.model';
import { DisplayPriority } from 'app/shared/metis/metis.util';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

export class Post extends Posting {
    public title?: string;
    public visibleForStudents?: boolean;
    public answers?: AnswerPost[];
    public tags?: string[];
    public plagiarismCase?: PlagiarismCase;
    public conversation?: Conversation;
    public displayPriority?: DisplayPriority;
    public resolved?: boolean;
    public isConsecutive?: boolean = false;

    constructor() {
        super();
        // set default values
        this.visibleForStudents = true;
        this.displayPriority = DisplayPriority.NONE;
    }
}
