package de.tum.in.www1.artemis.service;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextSubmission;

@Service
public class TextBlockService {

    public void prepopulateFeedbackBlocks(Result result) throws ClassCastException {
        if (result.getFeedbacks().size() != 0 || !(result.getSubmission() instanceof TextSubmission)) {
            return;
        }

        final TextSubmission textSubmission = (TextSubmission) result.getSubmission();
        final String submissionText = textSubmission.getText();
        final List<String> blocks = splitSubmissionIntoBlocks(submissionText);
        final List<Feedback> feedbacks = blocks.stream().map(block -> (new Feedback()).reference(block)).collect(Collectors.toList());

        result.getFeedbacks().addAll(feedbacks);
    }

    public List<String> splitSubmissionIntoBlocks(String submission) {
        BreakIterator breakIterator = BreakIterator.getSentenceInstance();
        breakIterator.setText(submission);
        List<String> sentences = new ArrayList<>();

        int start = breakIterator.first();
        for (int end = breakIterator.next(); end != BreakIterator.DONE; start = end, end = breakIterator.next()) {
            String sentence = submission.substring(start, end).trim();
            sentences.add(sentence);
        }

        return sentences;
    }

}
