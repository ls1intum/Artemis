import { Post } from 'app/entities/metis/post.model';
import { Posting } from 'app/entities/metis/posting.model';

export class AnswerPost extends Posting {
    public tutorApproved?: boolean;
    public post?: Post;

    constructor() {
        super();
        this.tutorApproved = false; // default value
    }
}
