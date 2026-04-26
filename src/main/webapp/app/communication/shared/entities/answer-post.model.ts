import { Post } from 'app/communication/shared/entities/post.model';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';

export class AnswerPost extends Posting {
    public resolvesPost?: boolean;
    public post?: Post;
    public forwardedPosts?: (Post | undefined)[] = [];
    public forwardedAnswerPosts?: (AnswerPost | undefined)[] = [];
    public confidenceScore?: number;
    public verified?: boolean;
    public verifiedBy?: User;
    public verifiedAt?: dayjs.Dayjs;

    constructor() {
        super();
        this.resolvesPost = false; // default value
    }
}
