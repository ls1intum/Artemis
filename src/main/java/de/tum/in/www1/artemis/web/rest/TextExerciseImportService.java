package de.tum.in.www1.artemis.web.rest;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;

@Repository
public class TextExerciseImportService {

    private final Logger log = LoggerFactory.getLogger(TextExerciseImportService.class);

    private final TextExerciseRepository textExerciseRepository;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    private final FeedbackRepository feedbackRepository;

    private final TextBlockRepository textBlockRepository;

    public TextExerciseImportService(TextExerciseRepository textExerciseRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, FeedbackRepository feedbackRepository, TextBlockRepository textBlockRepository) {
        this.textExerciseRepository = textExerciseRepository;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.feedbackRepository = feedbackRepository;
        this.textBlockRepository = textBlockRepository;
    }

    /**
     * Imports a text exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except Student-, Tutor participations and student questions . <br>
     * There are however, a couple of things that will never get copied:
     *
     * @param templateExercise The template exercise which should get imported
     * @param importedExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @Transactional
    public TextExercise importTextExerciseBasis(final TextExercise templateExercise, TextExercise importedExercise) {
        log.debug("Creating a new Exercise based on exercise", templateExercise);
        TextExercise newExercise = new TextExercise();
        newExercise.setCourse(importedExercise.getCourse());
        newExercise.setSampleSolution(importedExercise.getSampleSolution());
        newExercise.setTitle(importedExercise.getTitle());
        newExercise.setMaxScore(importedExercise.getMaxScore());
        newExercise.setAssessmentType(importedExercise.getAssessmentType());
        newExercise.setProblemStatement(importedExercise.getProblemStatement());
        newExercise.setCategories(importedExercise.getCategories());
        newExercise.setDifficulty(importedExercise.getDifficulty());
        newExercise.setMode(importedExercise.getMode());
        if (newExercise.getMode() == ExerciseMode.TEAM) {
            newExercise.setTeamAssignmentConfig(copyTeamAssignmentConfig(importedExercise.getTeamAssignmentConfig()));
        }
        textExerciseRepository.save(newExercise);

        // Create new submission
        newExercise.setExampleSubmissions(copyExampleSubmission(templateExercise, newExercise));
        return newExercise;
    }

    private TeamAssignmentConfig copyTeamAssignmentConfig(TeamAssignmentConfig originalConfig) {
        log.debug("Copying TeamAssignmentConfig");
        TeamAssignmentConfig newConfig = new TeamAssignmentConfig();
        newConfig.setMinTeamSize(originalConfig.getMinTeamSize());
        newConfig.setMaxTeamSize(originalConfig.getMaxTeamSize());
        return newConfig;
    }

    private Result copyResult(Result originalResult, Submission newSubmission) {
        log.debug("Copying the result to new submission: {}", newSubmission);
        Result newResult = new Result();
        // Save empty Result to get Id
        resultRepository.save(newResult);
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

    private List<Feedback> copyFeedback(List<Feedback> originalFeedbacks, Result newResult) {
        log.debug("Copying the feedbacks to new result: {}", newResult);
        List<Feedback> newFeedbacks = new ArrayList<>();
        for (final var originalFeedback : originalFeedbacks) {
            Feedback newFeedback = new Feedback();
            // save empty Feedback to get Id
            feedbackRepository.save(newFeedback);
            newFeedback.setCredits(originalFeedback.getCredits());
            newFeedback.setDetailText(originalFeedback.getDetailText());
            newFeedback.setPositive(originalFeedback.isPositive());
            newFeedback.setReference(originalFeedback.getReference());
            newFeedback.setType(originalFeedback.getType());
            newFeedback.setText(originalFeedback.getText());
            newFeedback.setResult(newResult);

            feedbackRepository.save(newFeedback);
            newFeedbacks.add(newFeedback);
        }
        return newFeedbacks;
    }

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

    private Set<ExampleSubmission> copyExampleSubmission(TextExercise templateExercise, TextExercise newExercise) {
        log.debug("Copying the ExampleSubmissions to new Exercise: {}", newExercise);
        Set<ExampleSubmission> newExampleSubmissions = new HashSet<>();
        for (ExampleSubmission originalExampleSubmission : templateExercise.getExampleSubmissions()) {
            TextSubmission originalSubmission = (TextSubmission) originalExampleSubmission.getSubmission();
            TextSubmission newSubmission = new TextSubmission();
            // save empty submission to get id
            submissionRepository.save(newSubmission);
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

            ExampleSubmission newExampleSubmission = new ExampleSubmission();
            newExampleSubmission.setExercise(newExercise);
            newExampleSubmission.setSubmission(newSubmission);
            newExampleSubmission.setAssessmentExplanation(originalExampleSubmission.getAssessmentExplanation());
            exampleSubmissionRepository.save(newExampleSubmission);
            newExampleSubmissions.add(newExampleSubmission);
        }
        return newExampleSubmissions;
    }
}
