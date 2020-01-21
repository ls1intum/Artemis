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

    private Random random = new Random();

    public ArrayList<TextBlock> generateTextBlocks(int count) {
        ArrayList<TextBlock> textBlocks = new ArrayList<>();
        TextBlock textBlock;
        for (int i = 0; i < count; i++) {
            textBlock = new TextBlock();
            textBlock.setText("TextBlock" + i);
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
}
