import { Post } from 'app/communication/shared/entities/post.model';
import { Posting } from 'app/communication/shared/entities/posting.model';

export class AnswerPost extends Posting {
    public resolvesPost?: boolean;
    public post?: Post;
    public forwardedPosts?: (Post | undefined)[] = [];
    public forwardedAnswerPosts?: (AnswerPost | undefined)[] = [];

    constructor() {
        super();
        this.resolvesPost = false; // default value
    }
}
