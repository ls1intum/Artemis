package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.Feedback;
import de.tum.cit.aet.artemis.domain.GradingInstruction;
import de.tum.cit.aet.artemis.domain.Result;
import de.tum.cit.aet.artemis.domain.Submission;
import de.tum.cit.aet.artemis.domain.TextBlock;
import de.tum.cit.aet.artemis.domain.TextBlockType;
import de.tum.cit.aet.artemis.domain.TextExercise;
import de.tum.cit.aet.artemis.domain.TextSubmission;
import de.tum.cit.aet.artemis.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.repository.ResultRepository;
import de.tum.cit.aet.artemis.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.service.metis.conversation.ChannelService;

@Profile(PROFILE_CORE)
@Service
public class TextExerciseImportService extends ExerciseImportService {

    private static final Logger log = LoggerFactory.getLogger(TextExerciseImportService.class);

    private final TextExerciseRepository textExerciseRepository;

    private final FeedbackRepository feedbackRepository;

    private final TextBlockRepository textBlockRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    private final ChannelService channelService;

    private final CompetencyProgressService competencyProgressService;

    public TextExerciseImportService(TextExerciseRepository textExerciseRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, TextBlockRepository textBlockRepository, FeedbackRepository feedbackRepository,
            TextSubmissionRepository textSubmissionRepository, ChannelService channelService, FeedbackService feedbackService,
            CompetencyProgressService competencyProgressService) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository, feedbackService);
        this.textBlockRepository = textBlockRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.feedbackRepository = feedbackRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.channelService = channelService;
        this.competencyProgressService = competencyProgressService;
    }

    /**
     * Imports a text exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except Student-, Tutor participations, and student questions. <br>
     * This method calls {@link #copyTextExerciseBasis(TextExercise, Map)} to set up the basis of the exercise
     * {@link #copyExampleSubmission(Exercise, Exercise, Map)} for a hard copy of the example submissions.
     *
     * @param templateExercise The template exercise which should get imported
     * @param importedExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @NotNull
    public TextExercise importTextExercise(final TextExercise templateExercise, TextExercise importedExercise) {
        log.debug("Creating a new Exercise based on exercise {}", templateExercise);
        Map<Long, GradingInstruction> gradingInstructionCopyTracker = new HashMap<>();
        TextExercise newExercise = copyTextExerciseBasis(importedExercise, gradingInstructionCopyTracker);
        if (newExercise.isExamExercise()) {
            // Disable feedback suggestions on exam exercises (currently not supported)
            newExercise.setFeedbackSuggestionModule(null);
        }

        TextExercise newTextExercise = textExerciseRepository.save(newExercise);

        channelService.createExerciseChannel(newTextExercise, Optional.ofNullable(importedExercise.getChannelName()));
        newExercise.setExampleSubmissions(copyExampleSubmission(templateExercise, newExercise, gradingInstructionCopyTracker));

        competencyProgressService.updateProgressByLearningObjectAsync(newTextExercise);

        return newExercise;
    }

    /**
     * This helper method copies all attributes of the {@code importedExercise} into the new exercise.
     * Here we ignore all external entities as well as the start-, end-, and assessment due date.
     *
     * @param importedExercise              The exercise from which to copy the basis
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return the cloned TextExercise basis
     */
    @NotNull
    private TextExercise copyTextExerciseBasis(TextExercise importedExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        log.debug("Copying the exercise basis from {}", importedExercise);
        TextExercise newExercise = new TextExercise();

        super.copyExerciseBasis(newExercise, importedExercise, gradingInstructionCopyTracker);
        newExercise.setExampleSolution(importedExercise.getExampleSolution());
        return newExercise;
    }

    /**
     * This helper functions does a hard copy of the text blocks and inserts them into {@code newSubmission}
     *
     * @param originalTextBlocks The original text blocks to be copied
     * @param newSubmission      The submission in which we enter the new text blocks
     * @return the cloned list of text blocks
     */
    private Set<TextBlock> copyTextBlocks(Set<TextBlock> originalTextBlocks, TextSubmission newSubmission) {
        log.debug("Copying the TextBlocks to new TextSubmission: {}", newSubmission);
        var newTextBlocks = new HashSet<TextBlock>();
        for (TextBlock originalTextBlock : originalTextBlocks) {
            TextBlock newTextBlock = new TextBlock();
            newTextBlock.setEndIndex(originalTextBlock.getEndIndex());
            newTextBlock.setStartIndex(originalTextBlock.getStartIndex());
            newTextBlock.setSubmission(newSubmission);
            newTextBlock.setText(originalTextBlock.getText());
            newTextBlock.computeId();
            if (originalTextBlock.getType() != null) {
                if (originalTextBlock.getType() == TextBlockType.AUTOMATIC) {
                    newTextBlock.automatic();
                }
                else {
                    newTextBlock.manual();
                }
            }
            textBlockRepository.save(newTextBlock);
            newTextBlocks.add(newTextBlock);
        }
        return newTextBlocks;
    }

    /**
     * This functions does a hard copy of the example submissions contained in {@code templateExercise}.
     * To copy the corresponding Submission entity this function calls {@link #copySubmission(Submission, Map)}}
     *
     * @param templateExercise              {TextExercise} The original exercise from which to fetch the example submissions
     * @param newExercise                   The new exercise in which we will insert the example submissions
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return The cloned set of example submissions
     */
    Set<ExampleSubmission> copyExampleSubmission(Exercise templateExercise, Exercise newExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        log.debug("Copying the ExampleSubmissions to new Exercise: {}", newExercise);
        Set<ExampleSubmission> newExampleSubmissions = new HashSet<>();
        for (ExampleSubmission originalExampleSubmission : templateExercise.getExampleSubmissions()) {
            TextSubmission originalSubmission = (TextSubmission) originalExampleSubmission.getSubmission();
            TextSubmission newSubmission = copySubmission(originalSubmission, gradingInstructionCopyTracker);

            ExampleSubmission newExampleSubmission = new ExampleSubmission();
            newExampleSubmission.setExercise(newExercise);
            newExampleSubmission.setSubmission(newSubmission);
            newExampleSubmission.setAssessmentExplanation(originalExampleSubmission.getAssessmentExplanation());

            exampleSubmissionRepository.save(newExampleSubmission);
            newExampleSubmissions.add(newExampleSubmission);
        }
        return newExampleSubmissions;
    }

    /**
     * This helper function does a hard copy of the {@code originalSubmission} and stores the values in {@code newSubmission}.
     * To copy the TextBlocks and the submission results this function calls {@link #copyTextBlocks(Set, TextSubmission)} and
     * {@link ExerciseImportService#copyExampleResult(Result, Submission, Map)} respectively.
     *
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @param originalSubmission            The original submission to be copied.
     * @return The cloned submission
     */
    TextSubmission copySubmission(final Submission originalSubmission, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
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
            newSubmission.addResult(copyExampleResult(originalSubmission.getLatestResult(), newSubmission, gradingInstructionCopyTracker));
            newSubmission = submissionRepository.saveAndFlush(newSubmission);
            newSubmission = textSubmissionRepository.findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(newSubmission.getId());

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
        Map<Integer, TextBlock> originalManualTextBlockMap = originalTextBlocks.stream().filter(textBlock -> textBlock.getType() == TextBlockType.MANUAL)
                .collect(Collectors.toMap(TextBlock::getStartIndex, Function.identity()));
        Map<Integer, TextBlock> nonManualTextBlockMap = originalTextBlocks.stream().filter(textBlock -> textBlock.getType() != TextBlockType.MANUAL)
                .collect(Collectors.toMap(TextBlock::getStartIndex, Function.identity()));

        Map<String, String> textBlockIdPair = new HashMap<>();

        // collect <original text block id, new text block id> pair, it will help to find the feedback which has old reference
        newSubmissionTextBlocks.forEach(newTextBlock -> {
            TextBlock oldTextBlock;
            if (newTextBlock.getType() == TextBlockType.MANUAL) {
                oldTextBlock = originalManualTextBlockMap.get(newTextBlock.getStartIndex());
            }
            else {
                oldTextBlock = nonManualTextBlockMap.get(newTextBlock.getStartIndex());
            }
            if (oldTextBlock != null) {
                textBlockIdPair.put(oldTextBlock.getId(), newTextBlock.getId());
            }
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
