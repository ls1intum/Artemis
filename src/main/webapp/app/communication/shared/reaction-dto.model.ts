import { Reaction } from 'app/communication/shared/entities/reaction.model';

export interface ReactionDTO {
    id?: number | null;
    user?: any;
    creationDate?: string | null;
    emojiId: string;
    postId?: number | null;
    answerPostId?: number | null;
}

export function toReactionDTO(reaction: Reaction): ReactionDTO {
    if (!reaction.emojiId) throw new Error('emojiId is required!');
    return {
        id: reaction.id ?? null,
        user: null,
        creationDate: null,
        emojiId: reaction.emojiId,
        postId: reaction.post?.id ?? null,
        answerPostId: reaction.answerPost?.id ?? null,
    };
}
