import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { Post } from 'app/communication/shared/entities/post.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';

export class Reaction {
    public id?: number;
    public user?: User;
    public creationDate?: dayjs.Dayjs;
    public emojiId?: string;
    public post?: Post;
    public answerPost?: AnswerPost;
}

export class CreateReactionDTO {
    emojiId?: string;
    relatedPostId?: number;

    constructor(emojiId?: string, relatedPostId?: number) {
        this.emojiId = emojiId;
        this.relatedPostId = relatedPostId;
    }

    /**
     * Converts a Reaction to a minimal API payload.
     */
    static fromReaction(reaction: Reaction): CreateReactionDTO {
        return new CreateReactionDTO(reaction.emojiId, reaction.post?.id ?? reaction.answerPost?.id);
    }
}
