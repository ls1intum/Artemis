package de.tum.in.www1.artemis.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.repository.TextBlockRepository;

public class TextBlockServiceTest {

    @Autowired
    private TextBlockRepository textBlockRepository;

    TextBlockService textBlockService;

    @BeforeEach
    public void prepareFreshService() {
        textBlockService = new TextBlockService(textBlockRepository);
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
        final var textBlocks = new ArrayList<>(textBlockService.splitSubmissionIntoBlocks(submission));

        assertThat(textBlocks, hasSize(1));
        assertThat(textBlocks.get(0).getText(), is(equalTo("Hello World.")));
    }

    @Test
    public void splitSubmissionIntoBlocksForTwoSentencesWithoutNewLine() {
        final TextSubmission submission = new TextSubmission(0L).text("Hello World. This is a Test.");
        final var textBlocks = new ArrayList<>(textBlockService.splitSubmissionIntoBlocks(submission));

        assertThat(textBlocks, hasSize(2));
        assertThat(textBlocks.get(0).getText(), is(equalTo("Hello World.")));
        assertThat(textBlocks.get(1).getText(), is(equalTo("This is a Test.")));
    }

    @Test
    public void splitSubmissionsIntoBlocksForManySentencesWithNewlinesWithoutFullstop() {
        final TextSubmission submission = new TextSubmission(0L).text("Hello World. This is a Test\n\n\nAnother Test");
        final var textBlocks = new ArrayList<>(textBlockService.splitSubmissionIntoBlocks(submission));

        assertThat(textBlocks, hasSize(3));
        assertThat(textBlocks.get(0).getText(), is(equalTo("Hello World.")));
        assertThat(textBlocks.get(1).getText(), is(equalTo("This is a Test")));
        assertThat(textBlocks.get(2).getText(), is(equalTo("Another Test")));
    }

    @Test
    public void splitSubmissionIntoBlocksForManySentencesWithoutPunctuation() {
        final TextSubmission submission = new TextSubmission(0L).text("Example:\nThis is the first example\n\nSection 2:\n- Here is a list\n- Of many bullet  points\n\n");
        final var textBlocks = new ArrayList<>(textBlockService.splitSubmissionIntoBlocks(submission));

        String[] sections = new String[] { "Example:", "This is the first example", "Section 2:", "- Here is a list", "- Of many bullet  points" };
        assertThat(textBlocks, hasSize(sections.length));
        for (int i = 0; i < sections.length; i++) {
            assertThat(textBlocks.get(i).getText(), is(equalTo(sections[i])));
        }
    }
}
