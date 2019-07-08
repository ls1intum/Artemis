package de.tum.in.www1.artemis.service;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.TextBlockRepository;

@Service
public class TextBlockService {

    private final TextBlockRepository textBlockRepository;

    TextBlockService(TextBlockRepository textBlockRepository) {
        this.textBlockRepository = textBlockRepository;
    }

    public void prepopulateFeedbackBlocks(Result result) throws ClassCastException {
        if (result.getFeedbacks().size() != 0 || !(result.getSubmission() instanceof TextSubmission)) {
            return;
        }

        final TextSubmission textSubmission = (TextSubmission) result.getSubmission();
        final String submissionText = textSubmission.getText();
        final List<TextBlock> blocks = splitSubmissionIntoBlocks(submissionText);
        final List<Feedback> feedbacks = blocks.stream().map(block -> (new Feedback()).reference(block.getText()).credits(0d)).collect(Collectors.toList());

        result.getFeedbacks().addAll(feedbacks);
    }

    public List<TextBlock> splitSubmissionIntoBlocks(String submission) {
        BreakIterator breakIterator = BreakIterator.getSentenceInstance();
        breakIterator.setText(submission);
        List<TextBlock> blocks = new ArrayList<>();

        int start = breakIterator.first();
        for (int end = breakIterator.next(); end != BreakIterator.DONE; start = end, end = breakIterator.next()) {
            String sentence = submission.substring(start, end).trim();
            final TextBlock textBlock = new TextBlock().text(sentence).startIndex(start).endIndex(end);
            textBlock.computeId();
            blocks.add(textBlock);
        }

        return blocks;
    }

}
