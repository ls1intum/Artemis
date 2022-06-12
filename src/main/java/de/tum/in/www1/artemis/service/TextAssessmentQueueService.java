package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.*;

import java.util.*;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;

@Service
@Profile("athene")
public class TextAssessmentQueueService {

    private final TextClusterRepository textClusterRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    public TextAssessmentQueueService(TextClusterRepository textClusterRepository, TextSubmissionRepository textSubmissionRepository) {
        this.textClusterRepository = textClusterRepository;
        this.textSubmissionRepository = textSubmissionRepository;
    }

    /**
     * Calculates the proposedTextSubmission for a given Text exercise. This means the Text exercise which should be assessed next
     *
     * @param textExercise the exercise for
     * @throws IllegalArgumentException if textExercise isn't automatically assessable
     * @return a TextSubmission with the highest information Gain if there is one
     */
    public Optional<TextSubmission> getProposedTextSubmission(TextExercise textExercise) {
        return getProposedTextSubmission(textExercise, null);
    }

    /**
     * Calculates the proposedTextSubmission for a given Text exercise
     *
     * @param textExercise the exercise for
     * @param languages  list of languages the submission which the returned submission should have if null all languages are allowed
     * @throws IllegalArgumentException if textExercise isn't automatically assessable
     * @return a TextSubmission with the highest information Gain if there is one
     */
    @Transactional(readOnly = true)
    public Optional<TextSubmission> getProposedTextSubmission(TextExercise textExercise, List<Language> languages) {

        if (!textExercise.isAutomaticAssessmentEnabled()) {
            throw new IllegalArgumentException("The TextExercise is not automatic assessable");
        }
        List<TextSubmission> textSubmissionList = getAllOpenTextSubmissions(textExercise);
        if (textSubmissionList.isEmpty()) {
            return Optional.empty();
        }
        Map<TextBlock, Double> smallerClusterMap = calculateSmallerClusterPercentageBatch(textSubmissionList);
        return textSubmissionList.stream().filter(textSubmission -> languages == null || languages.contains(textSubmission.getLanguage()))
                .max(Comparator.comparingDouble(textSubmission -> calculateInformationGain(textSubmission, smallerClusterMap)));
    }

    /**
     * Return all TextSubmission which are the latest TextSubmission of a Participation and doesn't have a Result so far
     * The corresponding TextBlocks and Participations are retrieved from the database
     * @param exercise Exercise for which all assessed submissions should be retrieved
     * @return List of all TextSubmission which aren't assessed at the Moment, but need assessment in the future.
     *
     */
    public List<TextSubmission> getAllOpenTextSubmissions(TextExercise exercise) {
        final List<TextSubmission> submissions = textSubmissionRepository.findByParticipation_ExerciseIdAndResultsIsNullAndSubmittedIsTrue(exercise.getId());

        final Set<Long> clusterIds = submissions.stream().flatMap(submission -> submission.getBlocks().stream()).map(TextBlock::getCluster).filter(Objects::nonNull)
                .map(TextCluster::getId).collect(toSet());

        // To prevent lazy loading many elements later on, we fetch all clusters with text blocks here.
        final Map<Long, TextCluster> textClusterMap = textClusterRepository.findAllByIdsWithEagerTextBlocks(clusterIds).stream()
                .collect(toMap(TextCluster::getId, textCluster -> textCluster));

        // link up clusters with eager blocks
        submissions.stream().flatMap(submission -> submission.getBlocks().stream()).forEach(textBlock -> {
            if (textBlock.getCluster() != null) {
                textBlock.setCluster(textClusterMap.get(textBlock.getCluster().getId()));
            }
        });

        return submissions.stream()
                .filter(submission -> submission.getParticipation().findLatestSubmission().isPresent() && submission == submission.getParticipation().findLatestSubmission().get())
                .collect(toList());
    }

    /**
     * Given an TextSubmission calculate the expected information gain
     * based on the smallerClusterMap and addedDistancesMap
     *
     * @param textSubmission the textSubmission
     * @param smallerClusterMap Map of TextBlocks to percentage of smaller clusters the TextBlock Cluster
     * @return information gain for the TextSubmission
     */
    private double calculateInformationGain(TextSubmission textSubmission, Map<TextBlock, Double> smallerClusterMap) {
        var textBlocks = textSubmission.getBlocks();
        double totalScore = 0.0;
        for (TextBlock textBlock : textBlocks) {
            if (textBlock.isAssessable() || textBlock.getCluster() == null || textBlock.getAddedDistance() == null) {
                continue;
            }
            double textBlockScore = textBlock.getAddedDistance();
            textBlockScore /= textBlock.getCluster().size();
            textBlockScore += smallerClusterMap.get(textBlock);
            totalScore += textBlockScore;
        }
        return totalScore;
    }

    /**
     * Sums up all the distances in the Blocks cluster
     *
     * @param textBlock textBlock for which the distance should be added
     * @param cluster in which the textBlock distance should be added up
     * @throws IllegalArgumentException if textBlock isn't an element of cluster
     * @return added Distance to all other text blocks in the cluster
     */
    private double calculateAddedDistance(TextBlock textBlock, TextCluster cluster) {
        if (!cluster.getBlocks().contains(textBlock)) {
            throw new IllegalArgumentException("textBlock must be an element of the cluster");
        }
        double[][] distanceMatrix = cluster.getDistanceMatrix();
        int blockID = cluster.getBlocks().indexOf(textBlock);
        // subtract 1 because the statement also included the distance to itself, but it shouldn't be included
        return Arrays.stream(distanceMatrix[blockID]).map(distance -> 1.0 - distance).sum() - 1;
    }

    /**
     * Calculates and sets all AddedDistances to a list of TextBlocks in a Cluster
     *
     * @param textBlockList list of the TextBlocks
     * @param textCluster Cluster
     */
    public void setAddedDistances(List<TextBlock> textBlockList, TextCluster textCluster) {
        textBlockList.forEach(textBlock -> {
            double addedDistance = calculateAddedDistance(textBlock, textCluster);
            textBlock.setAddedDistance(addedDistance);
        });
    }

    /**
     * Calculates the Percentages of Smaller Clusters for a list of textSubmissions
     * All TextSubmissions must have the same exercise
     * @param textSubmissionList for which the smaller clusters should be calculated
     * @throws IllegalArgumentException if not all TextSubmissions are from the same exercise
     * @return return a map where the textBlock is the key and smaller cluster percentage is the value
     * If a textBlock has no cluster or is already assessable, it isn't in the map
     */
    public Map<TextBlock, Double> calculateSmallerClusterPercentageBatch(List<TextSubmission> textSubmissionList) {
        Map<TextBlock, Double> result = new HashMap<>();
        if (textSubmissionList.isEmpty()) {
            return result;
        }
        Participation participation = textSubmissionList.get(0).getParticipation();
        TextExercise currentExercise = (TextExercise) participation.getExercise();
        List<TextCluster> clusters = textClusterRepository.findAllByExercise(currentExercise);

        if (textSubmissionList.stream().map(submission -> submission.getParticipation().getExercise()).anyMatch(elem -> elem != currentExercise)) {
            throw new IllegalArgumentException("All TextSubmissions have to be from the same Exercise");
        }
        textSubmissionList.forEach(textSubmission -> {
            textSubmission.getBlocks().forEach(textBlock -> {
                if (textBlock.getCluster() == null) {
                    return;
                }
                OptionalInt optionalLargestClusterSize = clusters.stream().mapToInt(TextCluster::openTextBlockCount).max();

                // if cluster is empty
                if (optionalLargestClusterSize.isEmpty()) {
                    result.put(textBlock, 0.0);
                    return;
                }
                // if cluster is the largest set to smaller percentage to 1
                if (optionalLargestClusterSize.getAsInt() == textBlock.getCluster().openTextBlockCount()) {
                    result.put(textBlock, 1.0);
                    return;
                }

                int smallerClusterCount = clusters.stream().mapToInt(TextCluster::openTextBlockCount).reduce(0, (sum, elem) -> {
                    if (elem < textBlock.getCluster().openTextBlockCount()) {
                        return sum + 1;
                    }
                    return sum;
                });
                result.put(textBlock, (double) smallerClusterCount / clusters.size());
            });
        });
        return result;
    }
}
