package de.tum.cit.aet.artemis.text.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseImportService;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextBlockType;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;

@Conditional(TextEnabled.class)
@Lazy
@Service
public class TextExerciseImportService extends ExerciseImportService {

    private static final Logger log = LoggerFactory.getLogger(TextExerciseImportService.class);

    private final TextExerciseRepository textExerciseRepository;

    private final FeedbackRepository feedbackRepository;

    private final TextBlockRepository textBlockRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    private final ChannelService channelService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final CompetencyExerciseLinkService competencyExerciseLinkService;

    public TextExerciseImportService(TextExerciseRepository textExerciseRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, TextBlockRepository textBlockRepository, FeedbackRepository feedbackRepository,
            TextSubmissionRepository textSubmissionRepository, ChannelService channelService, FeedbackService feedbackService,
            Optional<CompetencyProgressApi> competencyProgressApi, CompetencyExerciseLinkService competencyExerciseLinkService) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository, feedbackService);
        this.textBlockRepository = textBlockRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.feedbackRepository = feedbackRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.channelService = channelService;
        this.competencyProgressApi = competencyProgressApi;
        this.competencyExerciseLinkService = competencyExerciseLinkService;
    }

    /**
     * Imports a text exercise: builds a new entity from {@code newExercise} (the destination and any caller overrides),
     * backfills its basis from {@code sourceExercise} (the original), copies a hard copy of the example submissions, and
     * saves it. Student-/tutor participations are not copied.
     * This method calls {@link #copyTextExerciseBasis(TextExercise, TextExercise, Map)} to set up the basis of the
     * exercise and {@link #copyExampleSubmission(Exercise, Exercise, Map)} for a hard copy of the example submissions.
     *
     * @param newExercise    the exercise to build; already carries the destination (course / exercise group) and any overrides
     * @param sourceExercise the source exercise whose content is copied
     * @return The newly created exercise
     */
    @NonNull
    public TextExercise importTextExercise(final TextExercise newExercise, final TextExercise sourceExercise) {
        log.debug("Creating a new text exercise based on exercise {}", sourceExercise);
        Map<Long, GradingInstruction> gradingInstructionCopyTracker = new HashMap<>();
        copyTextExerciseBasis(newExercise, sourceExercise, gradingInstructionCopyTracker);

        var competencyLinks = competencyExerciseLinkService.extractCompetencyLinksForCreation(newExercise);
        // Only the first save is identity-preserving (the id was cleared, so Spring Data persists newExercise itself). The
        // second save operates on a detached entity and therefore merges into a new instance, so its result must be used:
        // otherwise the freshly added competency links keep their unset embedded id on the returned graph.
        TextExercise savedExercise = textExerciseRepository.save(newExercise);
        if (!competencyLinks.isEmpty()) {
            competencyExerciseLinkService.addCompetencyLinksForCreation(savedExercise, competencyLinks);
            savedExercise = textExerciseRepository.save(savedExercise);
        }
        final TextExercise persistedExercise = savedExercise;
        // The channel name is transient, so a merged copy does not carry it. Restore it so the serialized import response
        // reports the channel the caller asked for.
        persistedExercise.setChannelName(newExercise.getChannelName());

        channelService.createExerciseChannel(persistedExercise, Optional.ofNullable(persistedExercise.getChannelName()));
        persistedExercise.setExampleSubmissions(copyExampleSubmission(sourceExercise, persistedExercise, gradingInstructionCopyTracker));

        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(persistedExercise));

        return persistedExercise;
    }

    /**
     * Backfills the text exercise basis onto {@code newExercise} from {@code sourceExercise}: the generic basis follows
     * the "keep the caller's value, else take the source's" rule (see {@link ExerciseImportService#copyExerciseBasis}),
     * and the text-specific example solution likewise prefers the caller's edited value. All external entities and the
     * start-, end-, and assessment due dates are intentionally not copied here.
     *
     * @param newExercise                   the exercise being built; mutated in place
     * @param sourceExercise                the source exercise providing the content to backfill
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     */
    private void copyTextExerciseBasis(TextExercise newExercise, TextExercise sourceExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        log.debug("Copying the text exercise basis from {}", sourceExercise);
        prepareNewExerciseForImport(newExercise);
        super.copyExerciseBasis(newExercise, sourceExercise, gradingInstructionCopyTracker);
        // Prefer the caller's edited value (standalone import form), fall back to the source content.
        newExercise.setExampleSolution(firstNonNull(newExercise.getExampleSolution(), sourceExercise.getExampleSolution()));
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
     * This functions does a hard copy of the example submissions contained in {@code sourceExercise}.
     * To copy the corresponding Submission entity this function calls {@link #copySubmission(Submission, Map)}}
     *
     * @param sourceExercise                The source exercise from which to fetch the example submissions
     * @param newExercise                   The new exercise in which we will insert the example submissions
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return The cloned set of example submissions
     */
    private Set<ExampleSubmission> copyExampleSubmission(Exercise sourceExercise, Exercise newExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        log.debug("Copying the ExampleSubmissions to new Exercise: {}", newExercise);
        Set<ExampleSubmission> newExampleSubmissions = new HashSet<>();
        for (ExampleSubmission originalExampleSubmission : sourceExercise.getExampleSubmissions()) {
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
    public TextSubmission copySubmission(final Submission originalSubmission, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
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
        Set<Feedback> newFeedbackList = newResult.getFeedbacks();
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
        List<Feedback> savedFeedback = feedbackRepository.saveFeedbacks(new ArrayList<>(newFeedbackList));
        newResult.updateAllFeedbackItems(savedFeedback, false);
        resultRepository.save(newResult);
    }
}
