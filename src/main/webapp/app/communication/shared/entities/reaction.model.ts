import { User } from 'app/account/user/user.model';
import dayjs from 'dayjs/esm';
import { Post } from 'app/communication/shared/entities/post.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { PostingType } from 'app/communication/shared/entities/posting.model';

export class Reaction {
    public id?: number;
    public user?: User;
    public creationDate?: dayjs.Dayjs;
    public emojiId?: string;
    public post?: Post;
    public answerPost?: AnswerPost;
}

export class ReactionDTO {
    emojiId?: string;
    relatedPostId?: number;
    postingType?: PostingType;

    constructor(emojiId?: string, relatedPostId?: number, postingType?: PostingType) {
        this.emojiId = emojiId;
        this.relatedPostId = relatedPostId;
        this.postingType = postingType;
    }

    /**
     * Converts a Reaction to a minimal API payload.
     *
     * The id alone does not identify a posting, because posts and answer posts are numbered
     * independently and the same value regularly denotes one of each. postingType says which.
     */
    static fromReaction(reaction: Reaction): ReactionDTO {
        return reaction.post
            ? new ReactionDTO(reaction.emojiId, reaction.post.id, PostingType.POST)
            : new ReactionDTO(reaction.emojiId, reaction.answerPost?.id, PostingType.ANSWER);
    }
}
