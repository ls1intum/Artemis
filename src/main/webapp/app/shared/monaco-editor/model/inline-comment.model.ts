/**
 * Model for inline comments used in AI-assisted problem statement refinement.
 *
 * This model is designed to be lightweight and ephemeral (localStorage-based).
 * For future persistent comments, see the CommentThread system being developed separately.
 */

export type InlineCommentStatus = 'draft' | 'pending' | 'applying' | 'applied' | 'error';

/**
 * Represents an inline comment attached to specific lines in the problem statement.
 * Used for providing targeted instructions to the AI refinement system.
 */
export interface InlineComment {
    /** Unique identifier for this comment */
    id: string;

    /** Start line number (1-indexed) */
    startLine: number;

    /** End line number (1-indexed, inclusive) */
    endLine: number;

    /** User's instruction describing what should change */
    instruction: string;

    /** Current status of this comment */
    status: InlineCommentStatus;

    /** When this comment was created */
    createdAt: Date;
}

/**
 * Creates a new inline comment with a generated ID.
 */
export function createInlineComment(startLine: number, endLine: number, instruction: string): InlineComment {
    return {
        id: `comment-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`,
        startLine,
        endLine,
        instruction,
        status: 'draft',
        createdAt: new Date(),
    };
}

/**
 * Serializable version for localStorage persistence.
 */
export interface SerializedInlineComment {
    id: string;
    startLine: number;
    endLine: number;
    instruction: string;
    status: InlineCommentStatus;
    createdAt: string; // ISO date string
}

/**
 * Converts an InlineComment to its serializable form.
 */
export function serializeComment(comment: InlineComment): SerializedInlineComment {
    return {
        ...comment,
        createdAt: comment.createdAt.toISOString(),
    };
}

/**
 * Converts a serialized comment back to an InlineComment.
 */
export function deserializeComment(serialized: SerializedInlineComment): InlineComment {
    return {
        ...serialized,
        createdAt: new Date(serialized.createdAt),
    };
}
