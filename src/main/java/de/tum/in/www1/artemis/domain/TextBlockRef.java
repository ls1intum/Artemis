package de.tum.in.www1.artemis.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * A TextBlockRef. It is a combining class for TextBlock and Feedback.
 */
public class TextBlockRef implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private TextBlock block;

    private Feedback feedback;

    /**
     * Create a TextBlockRef from a TextBlock and a Feedback.
     */
    public TextBlockRef(TextBlock block, Feedback feedback) {
        this.block = block;
        this.feedback = feedback;
    }

    public TextBlock getBlock() {
        return block;
    }

    public void setBlock(TextBlock block) {
        this.block = block;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public void setFeedback(Feedback feedback) {
        this.feedback = feedback;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        TextBlockRef that = (TextBlockRef) other;
        return Objects.equals(block, that.block) && Objects.equals(feedback, that.feedback);
    }

    @Override
    public int hashCode() {
        return Objects.hash(block, feedback);
    }

    @Override
    public String toString() {
        return "TextBlockRef{" + "textBlock=" + block + ", feedback=" + feedback + '}';
    }
}
