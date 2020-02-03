package de.tum.in.www1.artemis.service;

import static java.lang.Integer.compare;
import static java.util.stream.Collectors.toList;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextSubmission;

@Service
public class TextBlockService {

    /**
     * As line breaks are stored and handled in UNIX style (also on Windows), we always use '\n' instead of the platform-dependent separator.
     */
    private static final String LINE_SEPARATOR = "\\n";

    private static final int LINE_SEPARATOR_LENGTH = LINE_SEPARATOR.length();

    public static final Comparator<TextBlock> compareByStartIndexReversed = (TextBlock first, TextBlock second) -> compare(second.getStartIndex(), first.getStartIndex());

    /**
     * Splits TextSubmission for a given Result into TextBlocks and saves them in the TextSubmission
     * @param result the result, which correspond to the TextSubmission, that gets split
     * @throws ClassCastException if Result doesn't correspond to a TextSubmission
     */
    public void prepopulateFeedbackBlocks(Result result) throws ClassCastException {
        if (result.getFeedbacks().size() != 0 || !(result.getSubmission() instanceof TextSubmission)) {
            return;
        }

        final TextSubmission textSubmission = (TextSubmission) result.getSubmission();
        final List<TextBlock> blocks = splitSubmissionIntoBlocks(textSubmission);
        textSubmission.setBlocks(blocks);
        final List<Feedback> feedbacks = blocks.stream().map(block -> (new Feedback()).reference(block.getText()).credits(0d)).collect(toList());

        result.getFeedbacks().addAll(feedbacks);
    }

    /**
     * Break down a Text Submission into its TextBlocks.
     * A Text Block is defined (for now) as a Sentence. Delimitation is defined by java.text.BreakIterator or Linebreaks.
     *
     * @param submission TextSubmission to split
     * @return List of TextBlocks
     */
    @Transactional(readOnly = true)
    public List<TextBlock> splitSubmissionIntoBlocks(TextSubmission submission) {
        final String submissionText = submission.getText();
        if (submissionText == null)
            return new ArrayList<>();
        // Return empty list for missing submission text.

        // Javas Sentence BreakIterator handles sentence splitting.
        BreakIterator breakIterator = BreakIterator.getSentenceInstance();
        breakIterator.setText(submissionText);
        List<TextBlock> blocks = new ArrayList<>();

        int start = breakIterator.first();

        // Iterate over Sentences
        for (int end = breakIterator.next(); end != BreakIterator.DONE; start = end, end = breakIterator.next()) {
            String sentence = submissionText.substring(start, end).trim();

            // The BreakIterator does not take linebreaks into account.
            // Therefore, we split each determined sentence by linebreaks.
            final String[] split = sentence.split(LINE_SEPARATOR);
            for (String lineOrSentence : split) {
                final int startIndex = start;
                final int endIndex = start + lineOrSentence.length();
                start = endIndex + LINE_SEPARATOR_LENGTH;
                if (startIndex == endIndex)
                    continue; // Do *not* define a text block for an empty line.

                final TextBlock textBlock = new TextBlock().text(lineOrSentence).startIndex(startIndex).endIndex(endIndex).submission(submission);
                textBlock.computeId();
                blocks.add(textBlock);
            }
        }

        return blocks;
    }

}
