package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;

@Service
public class TextExerciseImportService extends ExerciseImportService {

    private final Logger log = LoggerFactory.getLogger(TextExerciseImportService.class);

    private final TextExerciseRepository textExerciseRepository;

    private final FeedbackRepository feedbackRepository;

    private final TextBlockRepository textBlockRepository;

    public TextExerciseImportService(TextExerciseRepository textExerciseRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, TextBlockRepository textBlockRepository, FeedbackRepository feedbackRepository) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository);
        this.textBlockRepository = textBlockRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * Imports a text exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except Student-, Tutor participations, and student questions. <br>
     * This method calls {@link #copyTextExerciseBasis(TextExercise)} to set up the basis of the exercise
     * {@link #copyExampleSubmission(Exercise, Exercise)} for a hard copy of the example submissions.
     *
     * @param templateExercise The template exercise which should get imported
     * @param importedExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @NotNull
    public TextExercise importTextExercise(final TextExercise templateExercise, TextExercise importedExercise) {
        log.debug("Creating a new Exercise based on exercise {}", templateExercise);
        TextExercise newExercise = copyTextExerciseBasis(importedExercise);
        newExercise.setKnowledge(templateExercise.getKnowledge());
        textExerciseRepository.save(newExercise);
        newExercise.setExampleSubmissions(copyExampleSubmission(templateExercise, newExercise));
        return newExercise;
    }

    /** This helper method copies all attributes of the {@code importedExercise} into the new exercise.
     * Here we ignore all external entities as well as the start-, end-, and assessment due date.
     *
     * @param importedExercise The exercise from which to copy the basis
     * @return the cloned TextExercise basis
     */
    @NotNull
    private TextExercise copyTextExerciseBasis(TextExercise importedExercise) {
        log.debug("Copying the exercise basis from {}", importedExercise);
        TextExercise newExercise = new TextExercise();

        super.copyExerciseBasis(newExercise, importedExercise, new HashMap<>());
        newExercise.setExampleSolution(importedExercise.getExampleSolution());
        return newExercise;
    }

    /** This helper functions does a hard copy of the text blocks and inserts them into {@code newSubmission}
     *
     * @param originalTextBlocks The original text blocks to be copied
     * @param newSubmission The submission in which we enter the new text blocks
     * @return the cloned list of text blocks
     */
    private Set<TextBlock> copyTextBlocks(Set<TextBlock> originalTextBlocks, TextSubmission newSubmission) {
        log.debug("Copying the TextBlocks to new TextSubmission: {}", newSubmission);
        var newTextBlocks = new HashSet<TextBlock>();
        for (TextBlock originalTextBlock : originalTextBlocks) {
            TextBlock newTextBlock = new TextBlock();
            Optional.ofNullable(originalTextBlock.getAddedDistance()).ifPresent(newTextBlock::setAddedDistance);
            Optional.ofNullable(originalTextBlock.getCluster()).ifPresent(newTextBlock::setCluster);
            newTextBlock.setEndIndex(originalTextBlock.getEndIndex());
            newTextBlock.setStartIndex(originalTextBlock.getStartIndex());
            newTextBlock.setSubmission(newSubmission);
            newTextBlock.setText(originalTextBlock.getText());
            newTextBlock.computeId();
            textBlockRepository.save(newTextBlock);
            newTextBlocks.add(newTextBlock);
        }
        return newTextBlocks;
    }

    /** This functions does a hard copy of the example submissions contained in {@code templateExercise}.
     * To copy the corresponding Submission entity this function calls {@link #copySubmission(Submission)}
     *
     * @param templateExercise {TextExercise} The original exercise from which to fetch the example submissions
     * @param newExercise The new exercise in which we will insert the example submissions
     * @return The cloned set of example submissions
     */
    Set<ExampleSubmission> copyExampleSubmission(Exercise templateExercise, Exercise newExercise) {
        log.debug("Copying the ExampleSubmissions to new Exercise: {}", newExercise);
        Set<ExampleSubmission> newExampleSubmissions = new HashSet<>();
        for (ExampleSubmission originalExampleSubmission : templateExercise.getExampleSubmissions()) {
            TextSubmission originalSubmission = (TextSubmission) originalExampleSubmission.getSubmission();
            TextSubmission newSubmission = copySubmission(originalSubmission);

            ExampleSubmission newExampleSubmission = new ExampleSubmission();
            newExampleSubmission.setExercise(newExercise);
            newExampleSubmission.setSubmission(newSubmission);
            newExampleSubmission.setAssessmentExplanation(originalExampleSubmission.getAssessmentExplanation());

            exampleSubmissionRepository.save(newExampleSubmission);
            newExampleSubmissions.add(newExampleSubmission);
        }
        return newExampleSubmissions;
    }

    /** This helper function does a hard copy of the {@code originalSubmission} and stores the values in {@code newSubmission}.
     * To copy the TextBlocks and the submission results this function calls {@link #copyTextBlocks(Set, TextSubmission)} and
     * {@link ExerciseImportService#copyExampleResult(Result, Submission, Map)} respectively.
     *
     * @param originalSubmission The original submission to be copied.
     * @return The cloned submission
     */
    TextSubmission copySubmission(final Submission originalSubmission) {
        TextSubmission newSubmission = new TextSubmission();
        if (originalSubmission != null) {
            log.debug("Copying the Submission to new ExampleSubmission: {}", newSubmission);
            newSubmission.setExampleSubmission(true);
            newSubmission.setSubmissionDate(originalSubmission.getSubmissionDate());
            newSubmission.setLanguage(((TextSubmission) originalSubmission).getLanguage());
            newSubmission.setType(originalSubmission.getType());
            newSubmission.setParticipation(originalSubmission.getParticipation());
            newSubmission.setText(((TextSubmission) originalSubmission).getText());
            newSubmission = submissionRepository.saveAndFlush(newSubmission);
            newSubmission.setBlocks(copyTextBlocks(((TextSubmission) originalSubmission).getBlocks(), newSubmission));
            newSubmission.addResult(copyExampleResult(originalSubmission.getLatestResult(), newSubmission, new HashMap<>()));
            newSubmission = submissionRepository.saveAndFlush(newSubmission);
            updateFeedbackReferencesWithNewTextBlockIds(((TextSubmission) originalSubmission).getBlocks(), newSubmission);
        }
        return newSubmission;
    }

    /**
     * Updates the feedback references with new text block id after making hard copy of original submission
     * with this update operation, the feedback and newly created text blocks will be matched, and the submission will be copied
     * with its assessment successfully
     *
     * @param originalTextBlocks The original text blocks to be copied
     * @param newSubmission      The submission which has newly created text blocks
     */
    private void updateFeedbackReferencesWithNewTextBlockIds(Set<TextBlock> originalTextBlocks, TextSubmission newSubmission) {
        Result newResult = newSubmission.getLatestResult();
        List<Feedback> newFeedbackList = newResult.getFeedbacks();
        Set<TextBlock> newSubmissionTextBlocks = newSubmission.getBlocks();

        // first collect original text blocks as <startIndex, TextBlock> map, startIndex will help to match newly created text block with original text block
        Map<Integer, TextBlock> originalTextBlockMap = originalTextBlocks.stream().collect(Collectors.toMap(TextBlock::getStartIndex, Function.identity()));

        Map<String, String> textBlockIdPair = new HashMap<>();

        // collect <original text block id, new text block id> pair, it will help to find the feedback which has old reference
        newSubmissionTextBlocks.forEach(newTextBlock -> {
            TextBlock oldTextBlock = originalTextBlockMap.get(newTextBlock.getStartIndex());
            textBlockIdPair.put(oldTextBlock.getId(), newTextBlock.getId());
        });

        // for each feedback in result, update the reference with new text block id
        for (Feedback feedback : newFeedbackList) {
            feedback.setReference(textBlockIdPair.get(feedback.getReference()));
        }

        // save the feedback (that is not yet in the database) to prevent null index exception
        List<Feedback> savedFeedback = feedbackRepository.saveFeedbacks(newFeedbackList);
        newResult.updateAllFeedbackItems(savedFeedback, false);
        resultRepository.save(newResult);
    }
}
