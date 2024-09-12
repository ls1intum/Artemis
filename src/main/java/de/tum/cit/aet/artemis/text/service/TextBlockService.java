package de.tum.cit.aet.artemis.text.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.lang.Integer.compare;

import java.text.BreakIterator;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Profile(PROFILE_CORE)
@Service
public class TextBlockService {

    /**
     * As line breaks are stored and handled in UNIX style (also on Windows), we always use '\n' instead of the platform-dependent separator.
     */
    private static final String LINE_SEPARATOR = "\n";

    private static final int LINE_SEPARATOR_LENGTH = LINE_SEPARATOR.length();

    public static final Comparator<TextBlock> compareByStartIndexReversed = (TextBlock first, TextBlock second) -> compare(second.getStartIndex(), first.getStartIndex());

    private final TextBlockRepository textBlockRepository;

    public TextBlockService(TextBlockRepository textBlockRepository) {
        this.textBlockRepository = textBlockRepository;
    }

    public Set<TextBlock> findAllBySubmissionId(Long id) {
        return this.textBlockRepository.findAllBySubmissionId(id);
    }

    public Set<TextBlock> computeTextBlocksForSubmissionBasedOnSyntax(TextSubmission textSubmission) {
        final var blocks = splitSubmissionIntoBlocks(textSubmission);
        textSubmission.setBlocks(blocks);
        return blocks;
    }

    /**
     * Break down a Text Submission into its TextBlocks.
     * A Text Block is defined (for now) as a Sentence. Delimitation is defined by java.text.BreakIterator or Linebreaks.
     *
     * @param submission TextSubmission to split
     * @return List of TextBlocks
     */
    public Set<TextBlock> splitSubmissionIntoBlocks(TextSubmission submission) {

        // Return empty set for missing submission text.
        final String submissionText = submission.getText();
        if (submissionText == null) {
            return Collections.emptySet();
        }

        // Javas Sentence BreakIterator handles sentence splitting.
        BreakIterator breakIterator = BreakIterator.getSentenceInstance();
        breakIterator.setText(submissionText);
        final Set<TextBlock> blocks = new HashSet<>();
        int start = breakIterator.first();

        // Iterate over Sentences
        for (int end = breakIterator.next(); end != BreakIterator.DONE; start = end, end = breakIterator.next()) {
            final String sentence = submissionText.substring(start, end);

            // The BreakIterator does not take linebreaks into account.
            // Therefore, we split each determined sentence by linebreaks.
            final String[] split = sentence.split(LINE_SEPARATOR);
            for (String lineOrSentence : split) {
                final String lineOrSentenceTrimed = lineOrSentence.trim();
                final int offset = lineOrSentence.indexOf(lineOrSentenceTrimed);
                final int startIndex = start + offset;
                final int endIndex = startIndex + lineOrSentenceTrimed.length();
                start = start + lineOrSentence.length() + LINE_SEPARATOR_LENGTH;
                if (startIndex == endIndex || lineOrSentence.isBlank()) {
                    continue; // Do *not* define a text block for an empty line.
                }

                final TextBlock textBlock = new TextBlock().text(lineOrSentenceTrimed).startIndex(startIndex).endIndex(endIndex).submission(submission).automatic();
                textBlock.computeId();
                blocks.add(textBlock);
            }
        }

        return blocks;
    }

    /**
     * Save Iterable collection of text blocks.
     *
     * @param textBlocks Iterable of TextBlocks.
     */
    public void saveAll(Iterable<TextBlock> textBlocks) {
        textBlockRepository.saveAll(textBlocks);
    }

    public void deleteForSubmission(TextSubmission textSubmission) {
        textBlockRepository.deleteAllBySubmission_Id(textSubmission.getId());
    }
}
