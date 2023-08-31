package de.tum.in.www1.artemis.domain;

/**
 * A TextBlockRef. It is a combining class for TextBlock and Feedback.
 */
public record TextBlockRef(TextBlock block, Feedback feedback) {
}
