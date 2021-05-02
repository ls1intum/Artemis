package de.tum.in.www1.artemis.service;

import java.util.HashSet;
import java.util.Set;

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

    public TextExerciseImportService(TextExerciseRepository textExerciseRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, TextBlockRepository textBlockRepository) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository, textBlockRepository);
        this.textExerciseRepository = textExerciseRepository;
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
        textExerciseRepository.save(newExercise);
        newExercise.setExampleSubmissions(copyExampleSubmission(templateExercise, newExercise));
        return newExercise;
    }

    /** This helper method copies all attributes of the {@code importedExercise} into the new exercise.
     * Here we ignore all external entities as well as the start-, end-, and asseessment due date.
     *
     * @param importedExercise The exercise from which to copy the basis
     * @return the cloned TextExercise basis
     */
    @NotNull
    private TextExercise copyTextExerciseBasis(TextExercise importedExercise) {
        log.debug("Copying the exercise basis from {}", importedExercise);
        TextExercise newExercise = new TextExercise();

        super.copyExerciseBasis(newExercise, importedExercise);
        newExercise.setSampleSolution(importedExercise.getSampleSolution());
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
            newTextBlock.setAddedDistance(originalTextBlock.getAddedDistance());
            newTextBlock.setCluster(originalTextBlock.getCluster());
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
    @Override
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
     * {@link #copyExampleResult(Result, Submission)} respectively.
     *
     * @param originalSubmission The original submission to be copied.
     * @return The cloned submission
     */
    @Override
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
            newSubmission.addResult(copyExampleResult(originalSubmission.getLatestResult(), newSubmission));
            newSubmission = submissionRepository.saveAndFlush(newSubmission);
        }
        return newSubmission;
    }
}
