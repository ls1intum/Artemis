export const FEEDBACK_PREVIEW_CHARACTER_LIMIT = 300;

/**
 * Computes the feedback preview for feedback texts with multiple lines or feedback that is longer than {@link FEEDBACK_PREVIEW_CHARACTER_LIMIT} characters.
 * @param text The feedback detail text.
 * @return One line of text with at most {@link FEEDBACK_PREVIEW_CHARACTER_LIMIT} characters.
 */
export const computeFeedbackPreviewText = (text?: string): string | undefined => {
    if (!text) {
        return undefined;
    }

    if (text.includes('\n')) {
        // if there are multiple lines, only use the first one
        const firstLine = text.slice(0, text.indexOf('\n'));
        return firstLine.slice(0, FEEDBACK_PREVIEW_CHARACTER_LIMIT);
    }

    return text.slice(0, FEEDBACK_PREVIEW_CHARACTER_LIMIT);
};
