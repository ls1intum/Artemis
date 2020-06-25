package de.tum.in.www1.artemis.web.rest;

import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.repository.*;

@Repository
public class TextExerciseImportService {

    private final Logger log = LoggerFactory.getLogger(TextExerciseImportService.class);

    private final TextExerciseRepository textExerciseRepository;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    private final TextBlockRepository textBlockRepository;

    public TextExerciseImportService(TextExerciseRepository textExerciseRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, TextBlockRepository textBlockRepository) {
        this.textExerciseRepository = textExerciseRepository;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.textBlockRepository = textBlockRepository;
    }

    /**
     * Imports a text exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except Student-, Tutor participations, and student questions. <br>
     * This method calls {@link #copyTextExerciseBasis(TextExercise)} to set up the basis of the exercise
     * {@link #copyExampleSubmission(TextExercise, TextExercise)} for a hard copy of the example submissions.
     *
     * @param templateExercise The template exercise which should get imported
     * @param importedExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @Transactional
    public TextExercise importTextExercise(final TextExercise templateExercise, TextExercise importedExercise) {
        log.debug("Creating a new Exercise based on exercise {}", templateExercise);
        TextExercise newExercise = copyTextExerciseBasis(importedExercise);
        textExerciseRepository.save(newExercise);
        newExercise.setExampleSubmissions(copyExampleSubmission(templateExercise, newExercise));
        return newExercise;
    }

    /** This helper method copies all attributes of the {@code importedExercise} into the new exercise.
     * Here we ignore all external entities as well as the start-, end-, and assemessment due date.
     * If the exercise is a team exercise, this method calls {@link #copyTeamAssignmentConfig(TeamAssignmentConfig)}
     * to copy the Team assignment configuration
     *
     * @param importedExercise The exercise from which to copy the basis
     * @return the cloned TextExercise basis
     */
    @NotNull
    private TextExercise copyTextExerciseBasis(TextExercise importedExercise) {
        log.debug("Copying the exercise basis from {}", importedExercise);
        TextExercise newExercise = new TextExercise();
        if (importedExercise.hasCourse()) {
            newExercise.setCourse(importedExercise.getCourseViaExerciseGroupOrCourseMember());
        }
        else {
            newExercise.setExerciseGroup(importedExercise.getExerciseGroup());
        }

        newExercise.setSampleSolution(importedExercise.getSampleSolution());
        newExercise.setTitle(importedExercise.getTitle());
        newExercise.setMaxScore(importedExercise.getMaxScore());
        newExercise.setAssessmentType(importedExercise.getAssessmentType());
        newExercise.setProblemStatement(importedExercise.getProblemStatement());
        newExercise.setReleaseDate(importedExercise.getReleaseDate());
        newExercise.setDueDate(importedExercise.getDueDate());
        newExercise.setAssessmentDueDate(importedExercise.getAssessmentDueDate());
        newExercise.setDifficulty(importedExercise.getDifficulty());
        newExercise.setGradingInstructions(importedExercise.getGradingInstructions());
        newExercise.setGradingCriteria(copyGradingCriteria(importedExercise));
        if (newExercise.getExerciseGroup() != null) {
            newExercise.setMode(ExerciseMode.INDIVIDUAL);
        }
        else {
            newExercise.setCategories(importedExercise.getCategories());
            newExercise.setMode(importedExercise.getMode());
            if (newExercise.getMode() == ExerciseMode.TEAM) {
                newExercise.setTeamAssignmentConfig(copyTeamAssignmentConfig(importedExercise.getTeamAssignmentConfig()));
            }
        }
        return newExercise;
    }

    /** Helper method which does a hard copy of the Grading Criteria
     *
     * @param originalTextExercise The original exercise which contains the grading criteria to be imported
     * @return A clone of the grading criteria list
     */
    private List<GradingCriterion> copyGradingCriteria(TextExercise originalTextExercise) {
        log.debug("Copying the grading criteria from {}", originalTextExercise);
        List<GradingCriterion> newGradingCriteria = new ArrayList<>();
        for (GradingCriterion originalGradingCriterion : originalTextExercise.getGradingCriteria()) {
            GradingCriterion newGradingCriterion = new GradingCriterion();

            newGradingCriterion.setExercise(originalTextExercise);
            newGradingCriterion.setTitle(originalGradingCriterion.getTitle());

            newGradingCriterion.setStructuredGradingInstructions(copyGradingInstruction(originalGradingCriterion, newGradingCriterion));

            newGradingCriteria.add(newGradingCriterion);
        }
        return newGradingCriteria;
    }

    /** Helper method which does a hard copy of the Grading Instructions
     *
     * @param originalGradingCriterion The original grading criterion which contains the grading instructions
     * @param newGradingCriterion The cloned grading criterion in which we insert the grading instructions
     * @return A clone of the grading instruction list of the grading criterion
     */
    private List<GradingInstruction> copyGradingInstruction(GradingCriterion originalGradingCriterion, GradingCriterion newGradingCriterion) {
        log.debug("Copying the grading instructions for the following criterion {}", originalGradingCriterion);
        List<GradingInstruction> newGradingInstructions = new ArrayList<>();
        for (GradingInstruction originalGradingInstruction : originalGradingCriterion.getStructuredGradingInstructions()) {
            GradingInstruction newGradingInstruction = new GradingInstruction();
            newGradingInstruction.setCredits(originalGradingInstruction.getCredits());
            newGradingInstruction.setFeedback(originalGradingInstruction.getFeedback());
            newGradingInstruction.setGradingScale(originalGradingInstruction.getGradingScale());
            newGradingInstruction.setInstructionDescription(originalGradingInstruction.getInstructionDescription());
            newGradingInstruction.setUsageCount(originalGradingInstruction.getUsageCount());
            newGradingInstruction.setGradingCriterion(newGradingCriterion);

            newGradingInstructions.add(newGradingInstruction);
        }
        return newGradingInstructions;
    }

    /** Helper method which does a hard copy of the Team Assignment Configurations.
     *
     * @param originalConfig the original team assignment configuration to be copied.
     * @return The cloned configuration
     */
    private TeamAssignmentConfig copyTeamAssignmentConfig(TeamAssignmentConfig originalConfig) {
        log.debug("Copying TeamAssignmentConfig");
        TeamAssignmentConfig newConfig = new TeamAssignmentConfig();
        newConfig.setMinTeamSize(originalConfig.getMinTeamSize());
        newConfig.setMaxTeamSize(originalConfig.getMaxTeamSize());
        return newConfig;
    }

    /** This helper method does a hard copy of the result of a submission.
     * To copy the feedback, it calls {@link #copyFeedback(List, Result)}
     *
     * @param originalResult The original result to be copied
     * @param newSubmission The submission in which we link the result clone
     * @return The cloned result
     */
    private Result copyResult(Result originalResult, Submission newSubmission) {
        log.debug("Copying the result to new submission: {}", newSubmission);
        Result newResult = new Result();
        newResult.setAssessmentType(originalResult.getAssessmentType());
        newResult.setAssessor(originalResult.getAssessor());
        newResult.setCompletionDate(originalResult.getCompletionDate());
        newResult.setExampleResult(true);
        newResult.setRated(true);
        newResult.setResultString(originalResult.getResultString());
        newResult.setHasFeedback(originalResult.getHasFeedback());
        newResult.setScore(originalResult.getScore());
        newResult.setFeedbacks(copyFeedback(originalResult.getFeedbacks(), newResult));
        newResult.setSubmission(newSubmission);

        resultRepository.save(newResult);

        return newResult;
    }

    /** This helper functions does a hard copy of the feedbacks.
     *
     * @param originalFeedbacks The original list of feedbacks to be copied
     * @param newResult The result in which we link the new feedback
     * @return The cloned list of feedback
     */
    private List<Feedback> copyFeedback(List<Feedback> originalFeedbacks, Result newResult) {
        log.debug("Copying the feedbacks to new result: {}", newResult);
        List<Feedback> newFeedbacks = new ArrayList<>();
        for (final var originalFeedback : originalFeedbacks) {
            Feedback newFeedback = new Feedback();
            newFeedback.setCredits(originalFeedback.getCredits());
            newFeedback.setDetailText(originalFeedback.getDetailText());
            newFeedback.setPositive(originalFeedback.isPositive());
            newFeedback.setReference(originalFeedback.getReference());
            newFeedback.setType(originalFeedback.getType());
            newFeedback.setText(originalFeedback.getText());
            newFeedback.setResult(newResult);

            newFeedbacks.add(newFeedback);
        }
        return newFeedbacks;
    }

    /** This helper functions does a hard copy of the text blocks and inserts them into {@code newSubmission}
     *
     * @param originalTextBlocks The original text blocks to be copied
     * @param newSubmission The submission in which we enter the new text blocks
     * @return the cloned list of text blocks
     */
    private List<TextBlock> copyTextBlocks(List<TextBlock> originalTextBlocks, TextSubmission newSubmission) {
        log.debug("Copying the TextBlocks to new TextSubmission: {}", newSubmission);
        List<TextBlock> newTextBlocks = new ArrayList<>();
        for (TextBlock originalTextBlock : originalTextBlocks) {
            TextBlock newTextBlock = new TextBlock();
            newTextBlock.setAddedDistance(originalTextBlock.getAddedDistance());
            newTextBlock.setCluster(originalTextBlock.getCluster());
            newTextBlock.setEndIndex(originalTextBlock.getEndIndex());
            newTextBlock.setStartIndex(originalTextBlock.getStartIndex());
            newTextBlock.setSubmission(newSubmission);
            newTextBlock.setText(originalTextBlock.getText());

            textBlockRepository.save(newTextBlock);
            newTextBlocks.add(newTextBlock);
        }
        return newTextBlocks;
    }

    /** This functions does a hard copy of the example submissions contained in {@code templateExercise}.
     * To copy the corresponding Submission entity this function calls {@link #copySubmission(TextSubmission)}
     *
     * @param templateExercise {TextExercise} The original exercise from which to fetch the example submissions
     * @param newExercise The new exercise in which we will insert the example submissions
     * @return The cloned set of example submissions
     */
    private Set<ExampleSubmission> copyExampleSubmission(TextExercise templateExercise, TextExercise newExercise) {
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
     * To copy the TextBlocks and the submission results this function calls {@link #copyTextBlocks(List, TextSubmission)} and
     * {@link #copyResult(Result, Submission)} respectively.
     *
     * @param originalSubmission The original submission to be copied.
     * @return The cloned submission
     */
    private TextSubmission copySubmission(TextSubmission originalSubmission) {
        TextSubmission newSubmission = new TextSubmission();
        if (originalSubmission != null) {
            log.debug("Copying the Submission to new ExampleSubmission: {}", newSubmission);
            newSubmission.setExampleSubmission(true);
            newSubmission.setSubmissionDate(originalSubmission.getSubmissionDate());
            newSubmission.setLanguage(originalSubmission.getLanguage());
            newSubmission.setType(originalSubmission.getType());
            newSubmission.setParticipation(originalSubmission.getParticipation());
            newSubmission.setText(originalSubmission.getText());
            newSubmission.setBlocks(copyTextBlocks(originalSubmission.getBlocks(), newSubmission));
            newSubmission.setResult(copyResult(originalSubmission.getResult(), newSubmission));
            submissionRepository.save(newSubmission);
        }
        return newSubmission;
    }
}
