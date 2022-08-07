package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Objects;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextSubmission;

class TextBlockServiceTest {

    private TextBlockService textBlockService;

    @BeforeEach
    public void prepareFreshService() {
        textBlockService = new TextBlockService(null);
    }

    @Test
    void splitSubmissionIntoBlocksForEmptyText() {
        TextSubmission submission = new TextSubmission(0L);
        var textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);
        assertThat(textBlocks).isEmpty();

        submission = new TextSubmission(0L).text("");
        textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);
        assertThat(textBlocks).isEmpty();

        submission = new TextSubmission(0L).text("\n");
        textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);
        assertThat(textBlocks).isEmpty();

        submission = new TextSubmission(0L).text("\n\n\n\n");
        textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);
        assertThat(textBlocks).isEmpty();
    }

    @Test
    void splitSubmissionIntoBlocksForSingleSentence() {
        final TextSubmission submission = new TextSubmission(0L).text("Hello World.");
        final var textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);

        assertThat(textBlocks).hasSize(1);
        assertThat(textBlocks.iterator().next().getText()).isEqualTo("Hello World.");
    }

    @Test
    void splitSubmissionIntoBlocksForTwoSentencesWithoutNewLine() {
        final TextSubmission submission = new TextSubmission(0L).text("Hello World. This is a Test.");
        final var textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);

        assertThat(textBlocks).hasSize(2).has(textBlock("Hello World.")).has(textBlock("This is a Test."));
    }

    @Test
    void splitSubmissionsIntoBlocksForManySentencesWithNewlinesWithoutFullstop() {
        final TextSubmission submission = new TextSubmission(0L).text("Hello World. This is a Test\n\n\nAnother Test");
        final var textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);

        assertThat(textBlocks).hasSize(3).has(textBlock("Hello World.")).has(textBlock("This is a Test")).has(textBlock("Another Test"));
    }

    @Test
    void splitSubmissionIntoBlocksForManySentencesWithoutPunctuation() {
        final TextSubmission submission = new TextSubmission(0L).text("Example:\nThis is the first example\n\nSection 2:\n- Here is a list\n- Of many bullet  points\n\n");
        final var textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);

        String[] sections = new String[] { "Example:", "This is the first example", "Section 2:", "- Here is a list", "- Of many bullet  points" };
        assertThat(textBlocks).hasSize(sections.length);
        for (String section : sections) {
            assertThat(textBlocks).has(textBlock(section));
        }
    }

    @Test
    void respectLeadingWhitespace() {
        final var submission = new TextSubmission(0L).text("   Test.");
        final var textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);

        assertThat(textBlocks).hasSize(1).has(textBlock("Test."));

        final TextBlock textBlock = textBlocks.iterator().next();
        assertThat(textBlock.getStartIndex()).isEqualTo(3);
        assertThat(textBlock.getEndIndex()).isEqualTo(8);
    }

    @Test
    void respectLeadingTabs() {
        final var submission = new TextSubmission(0L).text("\t\t\tTest.");
        final var textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);

        assertThat(textBlocks).hasSize(1).has(textBlock("Test."));

        final TextBlock textBlock = textBlocks.iterator().next();
        assertThat(textBlock.getStartIndex()).isEqualTo(3);
        assertThat(textBlock.getEndIndex()).isEqualTo(8);
    }

    @Test
    void splitAssemblaCodeLinesIntoTextBlocks() {
        final var submission = new TextSubmission(0L).text("""
                \t\t\tALLOC 3\t\t\t\t\t\t\tLOAD 0
                \t\t\tREAD\t\t\t\t\t\t\tLOAD 2
                \t\t\tSTORE 0\t\t\t\t\t\t\tMUL
                \t\t\tCONST 1\t\t\t\t\t\t\tLOAD 1
                \t\t\tSTORE 1\t\t\t\t\t\t\tADD
                \t\t\tCONST 0\t\t\t\t\t\t\tSTORE 1
                \t\t\tSTORE 2\t\t\t\t\t\t\tJUMP if_else_end
                \t\t\t\t\t\t\t
                while_start:\tLOAD 2\t\t\telse:\t\t\t\tLOAD 1
                \t\t\tCONST 1\t\t\t\t\t\t\tNEG
                \t\t\tADD\t\t\t\t\t\t\t\tSTORE 1
                \t\t\tLOAD 0
                \t\t\tLEQ\t\t\t\tif_else_end:\t\tLOAD 2
                \t\t\tFJUMP after_while\t\t\t\t\tCONST 1\t
                \t\t\t\t\t\t\t\t\t\t\tSTORE 2
                \t\t\tLOAD 1\t\t\t\t\t\t\tJUMP while_start
                \t\t\tCONST 2
                \t\t\tMOD\t\t\tafter_while:\t\tLOAD 1
                \t\t\tCONST 1\t\t\t\t\t\t\tLOAD 2
                \t\t\tEQ\t\t\t\t\t\t\t\tDIV
                \t\t\tFJUMP else\t\t\t\t\t\tWRITE
                \t\t\t\t\t\t\t\t\t\t\tHALT
                """);
        final var textBlocks = textBlockService.splitSubmissionIntoBlocks(submission);

        assertThat(textBlocks).hasSize(21);
        assertThat(textBlocks).has(textBlock(3, 23, "ALLOC 3\t\t\t\t\t\t\tLOAD 0"));
        assertThat(textBlocks).has(textBlock(27, 44, "READ\t\t\t\t\t\t\tLOAD 2"));
        assertThat(textBlocks).has(textBlock(48, 65, "STORE 0\t\t\t\t\t\t\tMUL"));
        assertThat(textBlocks).has(textBlock(69, 89, "CONST 1\t\t\t\t\t\t\tLOAD 1"));
        assertThat(textBlocks).has(textBlock(93, 110, "STORE 1\t\t\t\t\t\t\tADD"));
        assertThat(textBlocks).has(textBlock(114, 135, "CONST 0\t\t\t\t\t\t\tSTORE 1"));
        assertThat(textBlocks).has(textBlock(139, 169, "STORE 2\t\t\t\t\t\t\tJUMP if_else_end"));
        assertThat(textBlocks).has(textBlock(178, 215, "while_start:\tLOAD 2\t\t\telse:\t\t\t\tLOAD 1"));
        assertThat(textBlocks).has(textBlock(219, 236, "CONST 1\t\t\t\t\t\t\tNEG"));
        assertThat(textBlocks).has(textBlock(240, 258, "ADD\t\t\t\t\t\t\t\tSTORE 1"));
        assertThat(textBlocks).has(textBlock(262, 268, "LOAD 0"));
        assertThat(textBlocks).has(textBlock(272, 299, "LEQ\t\t\t\tif_else_end:\t\tLOAD 2"));
        assertThat(textBlocks).has(textBlock(303, 332, "FJUMP after_while\t\t\t\t\tCONST 1"));
        assertThat(textBlocks).has(textBlock(345, 352, "STORE 2"));
        assertThat(textBlocks).has(textBlock(356, 385, "LOAD 1\t\t\t\t\t\t\tJUMP while_start"));
        assertThat(textBlocks).has(textBlock(389, 396, "CONST 2"));
        assertThat(textBlocks).has(textBlock(400, 426, "MOD\t\t\tafter_while:\t\tLOAD 1"));
        assertThat(textBlocks).has(textBlock(430, 450, "CONST 1\t\t\t\t\t\t\tLOAD 2"));
        assertThat(textBlocks).has(textBlock(454, 467, "EQ\t\t\t\t\t\t\t\tDIV"));
        assertThat(textBlocks).has(textBlock(471, 492, "FJUMP else\t\t\t\t\t\tWRITE"));
        assertThat(textBlocks).has(textBlock(504, 508, "HALT"));
    }

    private Condition<? super Collection<? extends TextBlock>> textBlock(String expectedText) {
        return new Condition<>(String.format("a text block with text=\"%s\"", expectedText)) {

            @Override
            public boolean matches(Collection<? extends TextBlock> value) {
                return value.stream().anyMatch(textBlock -> Objects.equals(textBlock.getText(), expectedText));
            }
        };
    }

    private Condition<? super Collection<? extends TextBlock>> textBlock(int expectedStartIndex, int expectedEndIndex, String expectedText) {
        return new Condition<>(String.format("a text block with startIndex=%d, endIndex=%d, and text=\"%s\"", expectedStartIndex, expectedEndIndex, expectedText)) {

            @Override
            public boolean matches(Collection<? extends TextBlock> value) {
                return value.stream().anyMatch(textBlock -> textBlock.getStartIndex() == expectedStartIndex && textBlock.getEndIndex() == expectedEndIndex
                        && Objects.equals(textBlock.getText(), expectedText));
            }
        };
    }
}
