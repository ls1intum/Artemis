package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextSubmission;

@Service
public class TextBlockService {

    public void prepopulateFeedbackBlocks(Result result) throws ClassCastException {
        if (result.getFeedbacks().size() != 0 || !(result.getSubmission() instanceof TextSubmission)) {
            return;
        }

        final TextSubmission textSubmission = (TextSubmission) result.getSubmission();
        final List<TextBlock> blocks = splitSubmissionIntoBlocks(textSubmission);
        final List<Feedback> feedbacks = blocks.stream().map(block -> (new Feedback()).reference(block.getText()).credits(0d)).collect(toList());

        result.getFeedbacks().addAll(feedbacks);
    }

    @Transactional(readOnly = true)
    public List<TextBlock> splitSubmissionIntoBlocks(TextSubmission submission) {
        final String submissionText = submission.getText();
        if (submissionText == null)
            return new ArrayList<>();

        BreakIterator breakIterator = BreakIterator.getSentenceInstance();
        breakIterator.setText(submissionText);
        List<TextBlock> blocks = new ArrayList<>();

        final String LINE_SEPARATOR = System.lineSeparator();
        final int LINE_SEPARATOR_LENGTH = LINE_SEPARATOR.length();
        int start = breakIterator.first();
        for (int end = breakIterator.next(); end != BreakIterator.DONE; start = end, end = breakIterator.next()) {
            String sentence = submissionText.substring(start, end).trim();
            final String[] split = sentence.split(LINE_SEPARATOR);
            for (String lineOrSentence : split) {
                final int startIndex = start;
                final int endIndex = start + lineOrSentence.length();
                start = endIndex + LINE_SEPARATOR_LENGTH;
                if (startIndex == endIndex)
                    continue;

                final TextBlock textBlock = new TextBlock().text(lineOrSentence).startIndex(startIndex).endIndex(endIndex).submission(submission);
                textBlock.computeId();
                blocks.add(textBlock);
            }
        }

        return blocks;
    }

}
