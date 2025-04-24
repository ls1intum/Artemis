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
    public isConsecutive?: boolean = false;
    public originalPostId?: number;
    public forwardedPosts?: Post[] = [];
    public forwardedAnswerPosts?: AnswerPost[] = [];

    constructor() {
        super();
        // set default values
        this.displayPriority = DisplayPriority.NONE;
    }
}
