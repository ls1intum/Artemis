package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.TextExerciseUtilService;

public class TextClusterUtilityServiceTest extends AbstractSpringIntegrationTest {

    @Autowired
    // SUT
    private TextClusterUtilityService textClusterUtilityService;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @Autowired
    private TextBlockRepository textBlockRepository;

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

    private List<TextBlock> textBlocks;

    private TextCluster textCluster;

    private Result result;

    @BeforeEach
    public void init() {
        textBlocks = textExerciseUtilService.generateTextBlocks(10);
        for(TextBlock textBlock: textBlocks) {
            textBlock.computeId();
        }
        TextExercise textExercise = new TextExercise();
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
        textBlockRepository.saveAll(textBlocks);
    }

    @AfterEach
    public void tearDown() {
        feedbackRepository.deleteAll();
        resultRepository.deleteAll();
        textSubmissionRepository.deleteAll();
        textBlockRepository.deleteAll();
        textClusterRepository.deleteAll();
        textExerciseRepository.deleteAll();
        studentParticipationRepository.deleteAll();
    }

    @Test
    public void createDistanceMatrixTest() {
        double[][] distanceMatrix = textExerciseUtilService.createDistanceMatrix(5);

        for (int i = 0; i < distanceMatrix.length; i++) {
            for (int j = 0; j < distanceMatrix[i].length; j++) {
                if (i == j) {
                    assertThat(distanceMatrix[i][j]).isEqualTo(0.0);
                }
                else {
                    assertThat(distanceMatrix[i][j]).isBetween(0.0, 1.0);
                }
            }
        }
    }

    @Test
    public void getCreditsOfTextBlockTest() {
        List<Feedback> feedbacks = new ArrayList<>();

        for (int i = 0; i < textBlocks.size(); i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits((double) i);
            feedbacks.add(feedback);
        }
        feedbackRepository.saveAll(feedbacks);

        for (int i = 0; i < textBlocks.size(); i++) {
            assertThat(textClusterUtilityService.getScoreOfTextBlock(textBlocks.get(i)).getAsDouble()).isEqualTo((double) i);
        }
    }

    @Test
    public void getCreditsOfTextBlockWithoutFeedbackTest() {
        List<Feedback> feedbacks = new ArrayList<>();

        for (int i = 0; i < textBlocks.size(); i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedbacks.add(feedback);
        }
        feedbackRepository.saveAll(feedbacks);

        for (int i = 0; i < textBlocks.size(); i++) {
            assertThat(textClusterUtilityService.getScoreOfTextBlock(textBlocks.get(i)).isEmpty()).isTrue();
        }
    }

    @Test
    public void getCommentOfTextBlockTest() {
        List<Feedback> feedbacks = new ArrayList<>();

        for (int i = 0; i < textBlocks.size(); i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setDetailText(Integer.toString(i));
            feedbacks.add(feedback);
        }
        feedbackRepository.saveAll(feedbacks);

        for (int i = 0; i < textBlocks.size(); i++) {
            Optional<String> comment = textClusterUtilityService.getCommentOfTextBlock(textBlocks.get(i));

            assertThat(comment.get()).isEqualTo(Integer.toString(i));
        }
    }

    @Test
    public void getCommentOfTextBlockWithoutDetailTextTest() {
        List<Feedback> feedbacks = new ArrayList<>();

        for (int i = 0; i < textBlocks.size(); i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedbacks.add(feedback);
        }
        feedbackRepository.saveAll(feedbacks);

        for (int i = 0; i < textBlocks.size(); i++) {
            Optional<String> comment = textClusterUtilityService.getCommentOfTextBlock(textBlocks.get(i));

            assertThat(comment.isEmpty()).isTrue();
        }
    }

    @Test
    public void getAssessedBlocksTest() {
        int numberOfFeedback = 5;
        List<Feedback> feedbacks = new ArrayList<>();

        for (int i = 0; i < numberOfFeedback; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(1.0);
            feedbacks.add(feedback);
        }
        feedbackRepository.saveAll(feedbacks);

        for (int i = 0; i < textBlocks.size(); i++) {
            assertThat(textClusterUtilityService.getAssessedBlocks(textBlocks.get(i).getCluster()).size()).isEqualTo(numberOfFeedback);
        }
    }

    @Test
    public void calculateScoreCoveragePercentageTest() {
        List<Feedback> feedbacks = new ArrayList<>();

        int numberOfFeedbackWithScoreOne = 4;
        int numberOfFeedbackWithScoreTwo = 3;

        for (int i = 0; i < numberOfFeedbackWithScoreOne; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(1.0);
            feedbacks.add(feedback);
        }

        for (int i = numberOfFeedbackWithScoreOne; i < numberOfFeedbackWithScoreOne + numberOfFeedbackWithScoreTwo; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(2.0);
            feedbacks.add(feedback);
        }
        feedbackRepository.saveAll(feedbacks);

        assertThat(textClusterUtilityService.calculateScoreCoveragePercentage(textBlocks.get(0)).getAsDouble()).isEqualTo(((double) (4.0 / 7.0)));
        assertThat(textClusterUtilityService.calculateScoreCoveragePercentage(textBlocks.get(5)).getAsDouble()).isEqualTo(((double) (3.0 / 7.0)));
        assertThat(textClusterUtilityService.calculateScoreCoveragePercentage(textBlocks.get(9)).isPresent()).as("textblock without feedback is not accounted in score coverage")
                .isFalse();
    }

    @Test
    public void filterTextClusterTest() {
        List<Feedback> feedbacks = new ArrayList<>();

        int numberOfFeedbackWithScoreOne = 4;
        int numberOfFeedbackWithScoreTwo = 3;

        for (int i = 0; i < numberOfFeedbackWithScoreOne; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(1.0);
            feedbacks.add(feedback);
        }

        for (int i = numberOfFeedbackWithScoreOne; i < numberOfFeedbackWithScoreOne + numberOfFeedbackWithScoreTwo; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(2.0);
            feedbacks.add(feedback);
        }

        feedbackRepository.saveAll(feedbacks);

        List<TextBlock> result = textClusterUtilityService.filterTextCluster(textCluster, 1.0);

        for (int i = 0; i < result.size(); i++) {
            assertThat(textClusterUtilityService.getScoreOfTextBlock(result.get(i)).getAsDouble()).isEqualTo(1.0);
        }
    }

    @Test
    public void numberOfScoreOccurenceTest() {
        List<Feedback> feedbacks = new ArrayList<>();
        int numberOfFeedbackWithScoreOne = 5;
        for (int i = 0; i < numberOfFeedbackWithScoreOne; i++) {
            Feedback feedback = new Feedback();
            feedback.setReference(textBlocks.get(i).getId());
            feedback.setResult(result);
            feedback.setCredits(1.0);
            feedbacks.add(feedback);
        }

        feedbackRepository.saveAll(feedbacks);

        assertThat(textClusterUtilityService.getNumberOfScoreOccurrence(textCluster, 1.0)).isEqualTo(numberOfFeedbackWithScoreOne);
    }

    @Test
    public void getGivenScoresTest() {
        Feedback firstFeedback = new Feedback();
        firstFeedback.setReference(textBlocks.get(0).getId());
        firstFeedback.setResult(result);
        firstFeedback.setCredits(1.0);
        Feedback secondFeedback = new Feedback();
        secondFeedback.setReference(textBlocks.get(1).getId());
        secondFeedback.setResult(result);
        secondFeedback.setCredits(1.0);
        Feedback thirdFeedback = new Feedback();
        thirdFeedback.setReference(textBlocks.get(2).getId());
        thirdFeedback.setResult(result);
        thirdFeedback.setCredits(2.0);
        feedbackRepository.save(firstFeedback);
        feedbackRepository.save(secondFeedback);
        feedbackRepository.save(thirdFeedback);

        HashSet<Double> result = textClusterUtilityService.getGivenScores(textCluster);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.contains(2.0)).isTrue();
        assertThat(result.contains(3.0)).isFalse();
    }

    @Test
    public void getNearestNeighborWithScoreTest() {
        TextBlock initialTextBlock = textCluster.getBlocks().get(0);
        Feedback initialFeedback = new Feedback();
        initialFeedback.setReference(initialTextBlock.getId());
        initialFeedback.setResult(result);
        initialFeedback.setCredits(1.0);

        feedbackRepository.save(initialFeedback);

        TextBlock firstTextBlock = textCluster.getBlocks().get(1);
        Feedback firstFeedback = new Feedback();
        firstFeedback.setReference(firstTextBlock.getId());
        firstFeedback.setResult(result);
        firstFeedback.setCredits(1.0);

        feedbackRepository.save(firstFeedback);

        TextBlock secondTextBlock = textCluster.getBlocks().get(2);
        Feedback secondFeedback = new Feedback();
        secondFeedback.setReference(secondTextBlock.getId());
        secondFeedback.setResult(result);
        secondFeedback.setCredits(1.0);

        feedbackRepository.save(secondFeedback);

        Optional<TextBlock> nearestNeighbor = textClusterUtilityService.getNearestNeighborWithScore(initialTextBlock, 1.0);

        // Get the text block which is not the nearest neighbor and not the initial text block,
        // since we randomly mock the distances between text blocks
        // .stream().filter().filter().max(comparing(distanceBetweenBlocks)).get() would have also done it with 3 text blocks
        Optional<TextBlock> otherTextBlock = Optional.of(textClusterUtilityService.getAssessedBlocks(textCluster).stream()
                .filter(elem -> elem.getId() != nearestNeighbor.get().getId()).filter(iter -> iter.getId() != initialTextBlock.getId()).collect(toList()).get(0));

        assertThat(textCluster.distanceBetweenBlocks(initialTextBlock, nearestNeighbor.get())).as("returned text block is the closest text block")
                .isLessThan(textCluster.distanceBetweenBlocks(initialTextBlock, otherTextBlock.get()));
    }

    @Test
    public void calculatedExpectedValueTest() {
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

        OptionalDouble expectedValue = textClusterUtilityService.calculateExpectedValue(textCluster);
        assertThat(expectedValue.getAsDouble()).isBetween(1.9, 1.91);
    }

    @Test
    public void calculateVarianceTest() {
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

        OptionalDouble variance = textClusterUtilityService.calculateVariance(textCluster);
        assertThat(variance.getAsDouble()).isBetween(0.89, 8.891);
    }

    @Test
    public void calculateStandardDeviationTest() {
        List<Feedback> feedbacks = new ArrayList<>();
        Percentage errorRate = Percentage.withPercentage(0.05);

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
        assertThat(textClusterUtilityService.calculateStandardDeviation(textCluster).getAsDouble()).isCloseTo(0.9433, errorRate);
    }

    @Test
    public void roundScoreTest() {
        Feedback feedbackWithScoreZero = new Feedback();
        feedbackWithScoreZero.setReference(textBlocks.get(0).getId());
        feedbackWithScoreZero.setResult(result);
        feedbackWithScoreZero.setCredits(0.0);
        Feedback feedbackWithScoreOne = new Feedback();
        feedbackWithScoreOne.setReference(textBlocks.get(1).getId());
        feedbackWithScoreOne.setResult(result);
        feedbackWithScoreOne.setCredits(1.0);
        feedbackRepository.save(feedbackWithScoreOne);
        feedbackRepository.save(feedbackWithScoreZero);

        OptionalDouble firstRoundedScore = textClusterUtilityService.roundScore(textBlocks.get(0), 0.4);
        OptionalDouble secondRoundedScore = textClusterUtilityService.roundScore(textBlocks.get(1), 10.0);

        assertThat(firstRoundedScore.getAsDouble()).isEqualTo(0.0);
        assertThat(secondRoundedScore.getAsDouble()).isEqualTo(1.0);
    }
}
