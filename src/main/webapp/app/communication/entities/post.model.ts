import { AnswerPost } from 'app/communication/entities/answer-post.model';
import { Posting } from 'app/communication/entities/posting.model';
import { DisplayPriority } from 'app/communication/metis.util';
import { PlagiarismCase } from 'app/plagiarism/shared/types/PlagiarismCase';

export class Post extends Posting {
    public title?: string;
    public answers?: AnswerPost[];
    public tags?: string[];
    public plagiarismCase?: PlagiarismCase;
    public displayPriority?: DisplayPriority;
    public resolved?: boolean;
    public forwardedPosts?: Post[] = [];
    public forwardedAnswerPosts?: AnswerPost[] = [];

    constructor() {
        super();
        // set default values
        this.displayPriority = DisplayPriority.NONE;
    }
}
