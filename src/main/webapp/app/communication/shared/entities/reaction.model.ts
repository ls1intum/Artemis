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

export interface CreatePayload {
    emojiId?: string;
    relatedPostId?: number;
}

/**
 * Converts a Reaction to a minimal API payload.
 */
export function toReactionDTO(reaction: Reaction): CreatePayload {
    return {
        emojiId: reaction.emojiId,
        relatedPostId: reaction.post?.id ?? reaction.answerPost?.id,
    };
}
