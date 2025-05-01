import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { DisplayPriority } from 'app/communication/metis.util';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';

export class Post extends Posting {
    public title?: string;
    public answers?: AnswerPost[];
    public tags?: string[];
    public plagiarismCase?: PlagiarismCase;
    public displayPriority?: DisplayPriority;
    public resolved?: boolean;
    public forwardedPosts?: (Post | undefined)[] = [];
    public forwardedAnswerPosts?: (AnswerPost | undefined)[] = [];
    public originalPostId?: number;

    constructor() {
        super();
        // set default values
        this.displayPriority = DisplayPriority.NONE;
    }
}
