import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Posting } from 'app/entities/metis/posting.model';
import { DisplayPriority } from 'app/shared/metis/metis.util';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';

export class Post extends Posting {
    public title?: string;
    public answers?: AnswerPost[];
    public tags?: string[];
    public plagiarismCase?: PlagiarismCase;
    public displayPriority?: DisplayPriority;
    public resolved?: boolean;
    public forwardedPosts?: (Post | null)[] = [];
    public forwardedAnswerPosts?: (AnswerPost | null)[] = [];

    constructor() {
        super();
        // set default values
        this.displayPriority = DisplayPriority.NONE;
    }
}
