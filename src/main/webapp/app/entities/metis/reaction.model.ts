import { User } from 'app/core/user/user.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import dayjs from 'dayjs/esm';

export class Reaction {
    public id?: number;
    public user?: User;
    public creationDate?: dayjs.Dayjs;
    public emojiId?: string;
    public post?: Post;
    public answerPost?: AnswerPost;
}
