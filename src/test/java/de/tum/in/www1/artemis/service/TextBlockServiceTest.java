package de.tum.in.www1.artemis.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.Objects;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextSubmission;

public class TextBlockServiceTest {

    private TextBlockService textBlockService;

    @BeforeEach
    public void prepareFreshService() {
        textBlockService = new TextBlockService(null);
    }

    @Test
    public void splitSubmissionIntoBlocksForEmptyText() {
        TextSubmission submission = new TextSubmission(0L);
        var textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);
        assertThat(textBlocks, hasSize(0));

        submission = new TextSubmission(0L).text("");
        textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);
        assertThat(textBlocks, hasSize(0));

        submission = new TextSubmission(0L).text("\n");
        textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);
        assertThat(textBlocks, hasSize(0));

        submission = new TextSubmission(0L).text("\n\n\n\n");
        textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);
        assertThat(textBlocks, hasSize(0));
    }

    @Test
    public void splitSubmissionIntoBlocksForSingleSentence() {
        final TextSubmission submission = new TextSubmission(0L).text("Hello World.");
        final var textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);

        assertThat(textBlocks, hasSize(1));
        assertThat(textBlocks.iterator().next().getText(), is(equalTo("Hello World.")));
    }

    @Test
    public void splitSubmissionIntoBlocksForTwoSentencesWithoutNewLine() {
        final TextSubmission submission = new TextSubmission(0L).text("Hello World. This is a Test.");
        final var textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);

        assertThat(textBlocks, hasSize(2));
        assertThat(textBlocks, hasItem(textBlockWithEqualText("Hello World.")));
        assertThat(textBlocks, hasItem(textBlockWithEqualText("This is a Test.")));
    }

    @Test
    public void splitSubmissionsIntoBlocksForManySentencesWithNewlinesWithoutFullstop() {
        final TextSubmission submission = new TextSubmission(0L).text("Hello World. This is a Test\n\n\nAnother Test");
        final var textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);

        assertThat(textBlocks, hasSize(3));
        assertThat(textBlocks, hasItem(textBlockWithEqualText("Hello World.")));
        assertThat(textBlocks, hasItem(textBlockWithEqualText("This is a Test")));
        assertThat(textBlocks, hasItem(textBlockWithEqualText("Another Test")));
    }

    @Test
    public void splitSubmissionIntoBlocksForManySentencesWithoutPunctuation() {
        final TextSubmission submission = new TextSubmission(0L).text("Example:\nThis is the first example\n\nSection 2:\n- Here is a list\n- Of many bullet  points\n\n");
        final var textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);

        String[] sections = new String[] { "Example:", "This is the first example", "Section 2:", "- Here is a list", "- Of many bullet  points" };
        assertThat(textBlocks, hasSize(sections.length));
        for (String section : sections) {
            assertThat(textBlocks, hasItem(textBlockWithEqualText(section)));
        }
    }

    private Matcher<TextBlock> textBlockWithEqualText(String expectedText) {
        return new TypeSafeMatcher<>() {

            @Override
            public void describeTo(Description description) {
                description.appendText("text block with \"text\" property set to").appendValue(expectedText);
            }

            @Override
            protected boolean matchesSafely(TextBlock item) {
                return Objects.equals(item.getText(), expectedText);
            }
        };
    }
}
