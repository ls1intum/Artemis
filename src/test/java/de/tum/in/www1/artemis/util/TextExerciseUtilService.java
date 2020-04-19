package de.tum.in.www1.artemis.util;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;

@Service
public class TextExerciseUtilService {

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private StudentParticipationRepository participationRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private TextClusterRepository textClusterRepository;

    private Random random = new Random();

    public ArrayList<TextBlock> createTextBlocks(int count) {
        ArrayList<TextBlock> textBlocks = new ArrayList<>();
        TextBlock textBlock;
        for (int i = 0; i < count; i++) {
            textBlock = new TextBlock();
            textBlock.setText(Integer.toString(i));
            textBlock.computeId();
            textBlocks.add(textBlock);
        }
        return textBlocks;
    }

    public List<TextCluster> addTextBlocksToCluster(List<TextBlock> textBlocks, int[] clusterSizes, TextExercise textExercise) {

        if (Arrays.stream(clusterSizes).sum() != textBlocks.size()) {
            throw new IllegalArgumentException("The clusterSizes sum has to be equal to the number of textBlocks");
        }

        // Create clusters
        ArrayList<TextCluster> clusters = new ArrayList<>();
        for (int i = 0; i < clusterSizes.length; i++) {
            clusters.add(new TextCluster().exercise(textExercise));
        }
        // Add all textblocks to a random cluster

        textBlocks.forEach(textBlock -> {
            int clusterIndex = random.nextInt(clusterSizes.length);
            // as long as cluster is full select another cluster
            while (clusterSizes[clusterIndex] == 0) {
                clusterIndex = random.nextInt(clusterSizes.length);
            }
            clusterSizes[clusterIndex]--;
            clusters.get(clusterIndex).addBlocks(textBlock);
        });
        return clusters;
    }

    public TextExercise createSampleTextExerciseWithSubmissions(Course course, List<TextBlock> textBlocks, int submissionCount, int submissionSize) {
        if (textBlocks.size() != submissionCount * submissionSize) {
            throw new IllegalArgumentException("number of textBlocks must be eqaul to submissionCount * submissionSize");
        }
        TextExercise textExercise = new TextExercise();
        textExercise.setCourse(course);
        textExercise.setTitle("Title");
        textExercise.setShortName("Shortname");
        textExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        textExercise = textExerciseRepository.save(textExercise);

        // submissions.length must be equal to studentParticipations.length;
        for (int i = 0; i < submissionCount; i++) {
            TextSubmission submission = new TextSubmission();
            StudentParticipation studentParticipation = new StudentParticipation();
            studentParticipation.setExercise(textExercise);
            studentParticipation = participationRepository.save(studentParticipation);
            submission.setParticipation(studentParticipation);
            submission.setLanguage(Language.ENGLISH);
            submission.setText("Test123");
            submission.setBlocks(textBlocks.subList(i * submissionSize, (i + 1) * submissionSize));
            submission.setSubmitted(true);
            submission.setSubmissionDate(ZonedDateTime.now());
            textBlocks.subList(i * submissionSize, (i + 1) * submissionSize).forEach(textBlock -> textBlock.setSubmission(submission));

            studentParticipation.addSubmissions(submission);
            textSubmissionRepository.save(submission);
        }
        return textExercise;
    }

    /**
     * Creates a Submission to a text exercise with text blocks
     * @param course Course of the submission
     * @param textBlocks Text blocks of the submission
     * @param textExercise Text exercise the submission is referring to
     * @return Submission
     */
    public Submission createSubmissionWithTextBlocks(Course course, List<TextBlock> textBlocks, TextExercise textExercise) {
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setExercise(textExercise);
        studentParticipation = participationRepository.save(studentParticipation);

        TextSubmission submission = new TextSubmission();
        submission.setParticipation(studentParticipation);
        submission.setLanguage(Language.ENGLISH);
        submission.setText("Text");
        submission.setBlocks(textBlocks);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        studentParticipation.addSubmissions(submission);
        return submission;
    }

    /**
     * Creates a text cluster with a text exercise and text blocks
     * @param textBlocks text blocks of the text cluster
     * @param exercise text exercise of the text cluster
     * @return TextCluster
     */
    public TextCluster createTextCluster(List<TextBlock> textBlocks, TextExercise exercise) {
        TextCluster cluster = new TextCluster();
        cluster.setBlocks(textBlocks);
        cluster.setDistanceMatrix(createDistanceMatrix(textBlocks.size()));
        cluster.setExercise(exercise);
        for (TextBlock textBlock : textBlocks) {
            textBlock.setCluster(cluster);
        }
        return cluster;
    }

    /**
     * Creates a symmetric matrix mocking the cosine distances between text blocks
     * @param size size of the matrix
     * @return double [][]
     */
    public double[][] createDistanceMatrix(int size) {
        double[][] result = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < i; j++) {

                // Generate random double between 0 and 1
                double value = random.nextDouble();

                if (value == 0.0 || value == 1.0) {
                    value = random.nextDouble();
                }
                result[i][j] = value;
                result[j][i] = value;
            }
        }

        // Set the distances of each text block to itself to 0.0
        for (int i = 0; i < result.length; i++) {
            for (int j = 0; j < result[i].length; j++) {
                if (i == j) {
                    result[i][j] = 0.0;
                }
            }
        }
        return result;
    }
}
