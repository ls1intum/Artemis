import { Post } from 'app/entities/metis/post.model';
import { Posting } from 'app/entities/metis/posting.model';

export class AnswerPost extends Posting {
    public resolvesPost?: boolean;
    public post?: Post;
    public forwardedPosts?: (Post | null)[] = [];
    public forwardedAnswerPosts?: (AnswerPost | null)[] = [];

    constructor() {
        super();
        this.resolvesPost = false; // default value
    }
}
