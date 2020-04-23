package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.TextFeedbackValidationService;
import de.tum.in.www1.artemis.util.TextExerciseUtilService;

public class AutomaticTextFeedbackServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    // SUT
    private AutomaticTextFeedbackService automaticTextFeedbackService;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private TextClusterUtilityService textClusterUtilityService;

    @Mock
    private TextFeedbackValidationService mockedTextFeedbackValidationService;

    @Mock
    private TextBlockRepository mockedTextBlockRepository;

    private List<TextBlock> textBlocks;

    private TextCluster textCluster;

    private TextExercise textExercise;

    private Result result;

    @BeforeEach
    public void setUp() {
        textBlocks = textExerciseUtilService.generateTextBlocks(10);

        textExercise = new TextExercise();
        textExerciseRepository.save(textExercise);

        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(textExercise);
        studentParticipationRepository.save(participation);

        TextSubmission submission = new TextSubmission();
        submission.setParticipation(participation);
        textSubmissionRepository.save(submission);

        result = new Result();
        result.setSubmission(submission);
        resultRepository.save(result);

        textCluster = textExerciseUtilService.createTextCluster(textBlocks, textExercise);

        textClusterRepository.save(textCluster);
        mockedTextBlockRepository.saveAll(textBlocks);

        for(TextBlock textBlock: textBlocks) {
            textBlock.computeId();
        }
        // Mock the text feedback validation service + text block repo to avoid network calls but still use the injected text cluster utility service
        automaticTextFeedbackService = new AutomaticTextFeedbackService(mockedTextBlockRepository, textClusterUtilityService, mockedTextFeedbackValidationService);
    }

    @AfterEach
    public void tearDown() {
        feedbackRepository.deleteAll();
        resultRepository.deleteAll();
        textSubmissionRepository.deleteAll();
        textClusterRepository.deleteAll();
        mockedTextBlockRepository.deleteAll();
        textExerciseRepository.deleteAll();
        studentParticipationRepository.deleteAll();
    }

    @Test
    public void createScoreWithFiveTimesScoreOne() {
        List<Feedback> feedbacks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(1.0);
            feedbacks.add(feedback);
        }
        feedbackRepository.saveAll(feedbacks);
        TextBlock candidate = textBlocks.get(5);

        double suggestedScore = automaticTextFeedbackService.createScore(candidate);

        assertThat(suggestedScore).isEqualTo(1.0);
    }

    @Test
    public void createScoreWithFiveTimesScoreOneAndOneTimeScoreTwo() {
        List<Feedback> feedbacks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(1.0);
            feedbacks.add(feedback);
        }

        for (int i = 5; i < 7; i++) {
            Feedback feedbackWithScoreTwo = new Feedback();
            feedbackWithScoreTwo.setReference(textBlocks.get(i).getId());
            feedbackWithScoreTwo.setResult(result);
            feedbackWithScoreTwo.setCredits(2.0);
            feedbacks.add(feedbackWithScoreTwo);
        }
        feedbackRepository.saveAll(feedbacks);
        TextBlock candidate = textBlocks.get(9);

        double suggestedScore = automaticTextFeedbackService.createScore(candidate);

        assertThat(suggestedScore).as("entire text cluster is weighted").isGreaterThan(1.0);
    }

    @Test
    public void createScoreWithManualDistanceMatrix() {
        List<Feedback> feedbacks = new ArrayList<>();
        List<TextBlock> textBlocks = textExerciseUtilService.generateTextBlocks(4);
        textCluster = textExerciseUtilService.createTextCluster(textBlocks, textExercise);
        double[][] distanceMatrix = { { 0, 0.1, 0.9, 0.9 }, { 0.1, 0, 0.5, 0.5 }, { 0.9, 0.5, 0, 0.1 }, { 0.9, 0.5, 0.1, 0 } };
        textCluster.setDistanceMatrix(distanceMatrix);
        Feedback feedbackWithScoreOne = new Feedback();
        feedbackWithScoreOne.setReference(textBlocks.get(1).getId());
        feedbackWithScoreOne.setResult(result);
        feedbackWithScoreOne.setCredits(1.0);
        feedbacks.add(feedbackWithScoreOne);
        for (int i = 2; i < 4; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(2.0);
            feedbacks.add(feedback);
        }
        feedbackRepository.saveAll(feedbacks);
        TextBlock candidate = textBlocks.get(0);

        double suggestedScore = automaticTextFeedbackService.createScore(candidate);
        double differenceToOne = Math.abs(suggestedScore - 1.0);
        double differenceToTwo = Math.abs(suggestedScore - 2.0);

        // @TODO: calculate this manually and restrict the test case
        assertThat(differenceToOne).as("suggested score will be closer to one than two").isLessThan(differenceToTwo);
    }

    @Test
    public void createCommentWithoutFeedbackPoolTest() {
        TextBlock candidate = textBlocks.get(0);
        String suggestedComment = automaticTextFeedbackService.createComment(candidate, 1.0);
        assertThat(suggestedComment).isEqualTo("");
    }

    @Test
    public void createCommentTest() {
        List<Feedback> feedbacks = new ArrayList<>();
        String givenComment = "The quick brown fox jumps over the lazy dog";
        List<TextBlock> textBlocks = textExerciseUtilService.generateTextBlocks(4);
        textCluster = textExerciseUtilService.createTextCluster(textBlocks, textExercise);
        double[][] distanceMatrix = { { 0, 0.1, 0.9, 0.9 }, { 0.1, 0, 0.5, 0.5 }, { 0.9, 0.5, 0, 0.1 }, { 0.9, 0.5, 0.1, 0 } };
        textCluster.setDistanceMatrix(distanceMatrix);
        for(TextBlock textBlock: textBlocks) {
            textBlock.computeId();
        }
        Feedback closestFeedback = new Feedback();
        closestFeedback.setReference(textBlocks.get(1).getId());
        closestFeedback.setResult(result);
        closestFeedback.setCredits(1.0);
        closestFeedback.setDetailText(givenComment);
        feedbacks.add(closestFeedback);
        for (int i = 2; i < textBlocks.size(); i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(1.0);
            feedback.setDetailText("lorem ipsum");
            feedbacks.add(feedback);
        }
        TextBlock candidate = textBlocks.get(0);
        feedbackRepository.saveAll(feedbacks);

        String suggestedComment = automaticTextFeedbackService.createComment(candidate, 1.0);

        assertThat(suggestedComment).as("nearest text block's comment was selected").isEqualTo(givenComment);
    }

    @Test
    public void verifyScoreTest() {
        List<Feedback> feedbacks = new ArrayList<>();
        Feedback feedbackWithScoreZero = new Feedback();
        feedbackWithScoreZero.setReference(textBlocks.get(0).getId());
        feedbackWithScoreZero.setResult(result);
        feedbackWithScoreZero.setCredits(0.0);
        feedbacks.add(feedbackWithScoreZero);
        for (int i = 1; i < 3; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(1.0);
            feedbacks.add(feedback);
        }
        for (int i = 3; i < 7; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(2.0);
            feedbacks.add(feedback);
        }
        for (int i = 7; i < 10; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(3.0);
            feedbacks.add(feedback);
        }
        feedbackRepository.saveAll(feedbacks);
        TextBlock candidate = new TextBlock();
        candidate.setCluster(textCluster);
        double suggestedScore = 3.0;

        assertThat(automaticTextFeedbackService.verifyScore(candidate, suggestedScore)).isFalse();
        suggestedScore = 2.5;
        assertThat(automaticTextFeedbackService.verifyScore(candidate, suggestedScore)).isTrue();
    }

    @Test
    public void validateFeedbackTest() throws NetworkingError {
        // Stub network call
        when(mockedTextFeedbackValidationService.validateFeedback(any(), any())).thenReturn(50.0);

        TextBlock candidate = textBlocks.get(0);
        Feedback suggestedFeedback = new Feedback();
        suggestedFeedback.setReference(candidate.getId());
        suggestedFeedback.setResult(result);
        suggestedFeedback.setCredits(1.0);
        List<Feedback> feedbacks = new ArrayList<>();
        for (int i = 1; i < 5; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(1.0);
            feedbacks.add(feedback);
        }

        feedbackRepository.saveAll(feedbacks);

        double confidencePercentage = automaticTextFeedbackService.calculateConfidence(candidate, suggestedFeedback);

        assertThat(confidencePercentage).isBetween(0.0, 100.0);
    }

    @Test
    public void createFeedbackWithoutTextClusterTest() {
        TextBlock candidate = new TextBlock();
        candidate.setId(textBlocks.get(0).getId());

        Optional<Feedback> suggestedFeedback = automaticTextFeedbackService.createFeedback(candidate, 2, 40.0);

        assertThat(suggestedFeedback.isEmpty()).as("text block has not been clustered").isTrue();
    }

    @Test
    public void createFeedbackWithScoreTest() {
        TextBlock candidate = textBlocks.get(0);
        Feedback feedback = new Feedback();
        feedback.setCredits(5.0);
        feedback.setReference(candidate.getId());
        feedback.setResult(result);

        feedbackRepository.save(feedback);

        Optional<Feedback> suggestedFeedback = automaticTextFeedbackService.createFeedback(candidate, 3, 40.0);

        assertThat(suggestedFeedback.isEmpty()).as("text block already has feedback").isTrue();
    }

    @Test
    public void createFeedbackWithFeedbackThresholdTest() throws NetworkingError {
        TextBlock candidate = textBlocks.get(0);
        Feedback feedback = new Feedback();
        feedback.setCredits(1.0);
        feedback.setReference(textBlocks.get(0).getId());
        feedback.setResult(result);
        feedbackRepository.save(feedback);

        when(mockedTextFeedbackValidationService.validateFeedback(any(), any())).thenReturn(50.0);

        Optional<Feedback> suggestedFeedback = automaticTextFeedbackService.createFeedback(candidate, 2, 40);

        assertThat(suggestedFeedback.isEmpty()).as("feedback pool does not exceed minimum threshold").isTrue();
    }

    @Test
    public void createFeedbackWithConfidencePercentageNotExceedingTest() throws NetworkingError {

        // Stub the network call
        when(mockedTextFeedbackValidationService.validateFeedback(any(), any())).thenReturn(5.0);
        List<Feedback> feedbacks = new ArrayList<>();
        TextBlock candidate = textBlocks.get(0);
        for (int i = 1; i < 5; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setCredits(1.0);
            feedback.setResult(result);
            feedbacks.add(feedback);
        }
        feedbackRepository.saveAll(feedbacks);

        Optional<Feedback> suggestedFeedback = automaticTextFeedbackService.createFeedback(candidate, 2, 40.0);

        assertThat(suggestedFeedback.isEmpty()).as("confidence does not exceed threshold").isTrue();
    }

    @Test
    public void createFeedbackWithConfidencePercentageExceedingTest() throws NetworkingError {

        // Stub the network call
        when(mockedTextFeedbackValidationService.validateFeedback(any(), any())).thenReturn(50.0);
        List<Feedback> feedbacks = new ArrayList<>();
        TextBlock candidate = textBlocks.get(0);
        for (int i = 1; i < 5; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setCredits(1.0);
            feedback.setResult(result);
            feedbacks.add(feedback);
        }
        feedbackRepository.saveAll(feedbacks);

        Optional<Feedback> suggestedFeedback = automaticTextFeedbackService.createFeedback(candidate, 2, 40.0);

        assertThat(suggestedFeedback.isPresent()).as("confidence exceeds threshold").isTrue();
        assertThat(suggestedFeedback.get().getDetailText()).isEqualTo("");
        assertThat(suggestedFeedback.get().getCredits()).isEqualTo(1.0);
    }

    /**
     * In this test case we try to resemble the usage of the suggestFeedback method in an actual course setting as close as possible.
     * The text submission consists of 2 text blocks, which are both clustered to the same text cluster (this part will differ from a real-word scenario)
     * The text cluster has 10 text blocks, 5 of which have been given the score 1
     * For the first text block the confidence percentage will be 60.0, for the second text block 30.0 (under threshold)
     * This should result in the result of the submission consisting of one automatic feedback and one manual feedback.
     */
    @Test
    public void suggestFeedbackTest() throws NetworkingError {
        List<Feedback> feedbacks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Feedback feedback = new Feedback();
            feedback.setCredits(1.0);
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setDetailText(Integer.toString(i));
            feedbacks.add(feedback);
        }
        feedbackRepository.saveAll(feedbacks);

        TextSubmission textSubmission = new TextSubmission();
        textSubmission.setId(2L);
        textSubmissionRepository.save(textSubmission);
        List<TextBlock> submissionBlocks = new ArrayList<>();
        TextBlock firstTextBlock = new TextBlock();
        firstTextBlock.setSubmission(textSubmission);
        firstTextBlock.computeId();
        TextBlock secondTextBlock = new TextBlock();
        secondTextBlock.setSubmission(textSubmission);

        // Set a different start index to the first text block otherwise the same hash id will be computed
        secondTextBlock.setStartIndex(5);
        secondTextBlock.computeId();
        submissionBlocks.add(firstTextBlock);
        submissionBlocks.add(secondTextBlock);

        textBlocks.addAll(submissionBlocks);
        textCluster = textExerciseUtilService.createTextCluster(textBlocks, textExercise);
        firstTextBlock.setCluster(textCluster);
        secondTextBlock.setCluster(textCluster);

        // Stub the network calls
        when(mockedTextFeedbackValidationService.validateFeedback(eq(firstTextBlock), anyList())).thenReturn(60.0);
        when(mockedTextFeedbackValidationService.validateFeedback(eq(secondTextBlock), anyList())).thenReturn(30.0);

        // Stub db query
        when(mockedTextBlockRepository.findAllWithEagerClusterBySubmissionId(any())).thenReturn(submissionBlocks);

        Result submissionResult = new Result();
        submissionResult.setSubmission(textSubmission);

        automaticTextFeedbackService.suggestFeedback(submissionResult);

        int feedbackCount = submissionResult.getFeedbacks().size();

        int automaticFeedbackCount = submissionResult.getFeedbacks().stream().filter(feedbackIterator -> feedbackIterator.getType() == FeedbackType.AUTOMATIC).collect(toList())
                .size();

        int manualFeedbackCount = submissionResult.getFeedbacks().stream().filter(feedbackIterator -> feedbackIterator.getType() == FeedbackType.MANUAL).collect(toList()).size();

        assertThat(feedbackCount).as("every text block of the submission has received feedback").isEqualTo(submissionBlocks.size());
        assertThat(automaticFeedbackCount).as("one text block of the submission has received automatic feedback").isEqualTo(1);
        assertThat(manualFeedbackCount).as("one text block of the submission has received manual feedback").isEqualTo(1);
    }
}
