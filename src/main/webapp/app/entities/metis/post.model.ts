import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Posting } from 'app/entities/metis/posting.model';
import { DisplayPriority } from 'app/communication/metis.util';
import { PlagiarismCase } from 'app/plagiarism/shared/types/PlagiarismCase';

export class Post extends Posting {
    public title?: string;
    public answers?: AnswerPost[];
    public tags?: string[];
    public plagiarismCase?: PlagiarismCase;
    public displayPriority?: DisplayPriority;
    public resolved?: boolean;
    public isConsecutive?: boolean = false;
    public originalAnswerId?: number;
    public forwardedPosts?: Post[] = [];
    public forwardedAnswerPosts?: AnswerPost[] = [];

    constructor() {
        super();
        // set default values
        this.displayPriority = DisplayPriority.NONE;
    }
}
