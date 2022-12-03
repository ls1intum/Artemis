package de.tum.in.www1.artemis.util;

import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;

import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

@Service
public class TextExerciseUtilService {

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private StudentParticipationRepository participationRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    private final Random random = new Random();

    /**
     * Generate a set of specified size containing TextBlocks with dummy Text
     * @param count expected size of TextBlock set
     * @return Set of dummy TextBlocks
     */
    public Set<TextBlock> generateTextBlocks(int count) {
        Set<TextBlock> textBlocks = new HashSet<>();
        TextBlock textBlock;
        for (int i = 0; i < count; i++) {
            textBlock = new TextBlock();
            textBlock.setText("TextBlock" + i);
            textBlocks.add(textBlock);
        }
        return textBlocks;
    }

    /**
     * Generate a set of specified size containing TextBlocks with the same text
     * @param count expected size of TextBlock set
     * @return Set of TextBlocks with identical texts
     */
    public Set<TextBlock> generateTextBlocksWithIdenticalTexts(int count) {
        Set<TextBlock> textBlocks = new HashSet<>();
        TextBlock textBlock;
        String text = "TextBlock" + random.nextInt();

        for (int i = 0; i < count; i++) {
            String blockId = sha1Hex("id" + i + text);
            textBlock = new TextBlock();
            textBlock.setText(text);
            textBlock.setId(blockId);
            textBlock.automatic();
            textBlocks.add(textBlock);
        }
        return textBlocks;
    }

    /**
     * Create n TextClusters and randomly assign TextBlocks to new clusters.
     * @param textBlocks TextBlocks to fake cluster
     * @param clusterSizes Number of new clusters
     * @param textExercise TextExercise
     * @return List of TextClusters with assigned TextBlocks
     */
    public List<TextCluster> addTextBlocksToCluster(Set<TextBlock> textBlocks, int[] clusterSizes, TextExercise textExercise) {

        if (Arrays.stream(clusterSizes).sum() != textBlocks.size()) {
            throw new IllegalArgumentException("The clusterSizes sum has to be equal to the number of textBlocks");
        }

        // Create clusters
        List<TextCluster> clusters = new ArrayList<>();
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
            clusters.get(clusterIndex).removeBlocks(textBlock);
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
            studentParticipation.setParticipant(userRepository.getUser());
            studentParticipation.setExercise(textExercise);
            studentParticipation = participationRepository.save(studentParticipation);

            submission.setParticipation(studentParticipation);
            submission.setLanguage(Language.ENGLISH);
            submission.setText("Test123");
            submission.setBlocks(new HashSet<>(textBlocks.subList(i * submissionSize, (i + 1) * submissionSize)));
            submission.setSubmitted(true);
            submission.setSubmissionDate(ZonedDateTime.now());
            textBlocks.subList(i * submissionSize, (i + 1) * submissionSize).forEach(textBlock -> textBlock.setSubmission(submission));

            studentParticipation.addSubmission(submission);
            textSubmissionRepository.save(submission);

            textExercise.getStudentParticipations().add(studentParticipation);
        }
        return textExercise;
    }
}
