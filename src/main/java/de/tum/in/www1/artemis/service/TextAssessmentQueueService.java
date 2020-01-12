package de.tum.in.www1.artemis.service;

import java.util.*;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.repository.TextClusterRepository;

@Service
@Profile("automaticText")
public class TextAssessmentQueueService {

    private final TextClusterRepository textClusterRepository;

    private final TextSubmissionService textSubmissionService;

    public TextAssessmentQueueService(TextClusterRepository textClusterRepository, @Lazy TextSubmissionService textSubmissionService) {
        this.textClusterRepository = textClusterRepository;
        this.textSubmissionService = textSubmissionService;
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
        List<TextSubmission> textSubmissionList = textSubmissionService.getAllOpenTextSubmissions(textExercise);
        if (textSubmissionList.isEmpty()) {
            return Optional.empty();
        }
        HashMap<TextBlock, Double> smallerClusterMap = calculateSmallerClusterPercentageBatch(textSubmissionList);
        Optional<TextSubmission> best = textSubmissionList.stream().filter(textSubmission -> languages == null || languages.contains(textSubmission.getLanguage()))
                .max(Comparator.comparingDouble(textSubmission -> calculateInformationGain(textSubmission, smallerClusterMap)));
        return best;
    }

    /**
     * Given an TextSubmission calculate the expected information gain
     * based on the smallerClusterMap and addedDistancesMap
     *
     * @param textSubmission the textSubmission
     * @param smallerClusterMap Map of TextBlocks to percentage of smaller clusters the TextBlock Cluster
     * @return information gain for the TextSubmission
     */
    private double calculateInformationGain(TextSubmission textSubmission, HashMap<TextBlock, Double> smallerClusterMap) {
        List<TextBlock> textBlocks = textSubmission.getBlocks();
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
     * @return added Distance to all other textblocks in the cluster
     */
    private double calculateAddedDistance(TextBlock textBlock, TextCluster cluster) {
        if (!cluster.getBlocks().contains(textBlock)) {
            throw new IllegalArgumentException("textBlock must be an element of the cluster");
        }
        double[][] distanceMatrix = cluster.getDistanceMatrix();
        int blockID = cluster.getBlocks().indexOf(textBlock);
        // subtract 1 because the statement also included the distance to itself, but it should't be included
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
     * @return return a HashMap where the textBlock is the key and smaller cluster percentage is the value
     * If a textBlock has no cluster or is already assessable, it isn't in the HashMap
     */
    public HashMap<TextBlock, Double> calculateSmallerClusterPercentageBatch(List<TextSubmission> textSubmissionList) {
        HashMap<TextBlock, Double> result = new HashMap<>();
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
