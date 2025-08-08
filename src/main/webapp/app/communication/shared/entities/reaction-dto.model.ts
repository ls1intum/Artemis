import { Reaction } from 'app/communication/shared/entities/reaction.model';

/**
 * Data Transfer Object for Reaction.
 */
export interface ReactionDTO {
    id?: number;
    creationDate?: string;
    emojiId?: string;
    relatedPostId?: number;
}

/**
 * Converts a Reaction entity to a ReactionDTO for API communication.
 * @param reaction The Reaction entity to convert.
 */
export function toReactionDTO(reaction: Reaction): ReactionDTO {
    return {
        id: reaction.id ?? undefined,
        creationDate: reaction.creationDate ? reaction.creationDate.toISOString() : undefined,
        emojiId: reaction.emojiId,
        relatedPostId: reaction.post?.id ?? reaction.answerPost?.id,
    };
}
