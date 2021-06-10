import { User } from 'app/core/user/user.model';
import { Moment } from 'moment';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

export class Reaction {
    public id?: number;
    public author?: User;
    public creationDate?: Moment;
    public emojiId?: string;
    public post?: Post;
    public answerPost?: AnswerPost;

    constructor() {}
}
