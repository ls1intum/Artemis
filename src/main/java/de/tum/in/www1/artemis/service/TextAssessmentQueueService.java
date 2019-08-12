package de.tum.in.www1.artemis.service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.TextClusterRepository;

@Service
@Profile("automaticText")
public class TextAssessmentQueueService {

    private final ParticipationService participationService;

    private final TextClusterRepository textClusterRepository;

    private Instant start;

    private boolean running = false;

    public TextAssessmentQueueService(ParticipationService participationService, TextClusterRepository textClusterRepository) {
        this.participationService = participationService;
        this.textClusterRepository = textClusterRepository;
    }

    /**
     * Calculates the proposedTextSubmission for a given Text exercise
     *
     * @param textExercise the exercise for
     * @throws IllegalArgumentException if textExercise isn't automatically assessable
     * @return a TextSubmission with the highest information Gain if there is one
     */
    public Optional<TextSubmission> getProposedTextSubmission(TextExercise textExercise) {

        if (!textExercise.isAutomaticAssessmentEnabled()) {
            throw new IllegalArgumentException("The TextExercise has to be  automatic assessable");
        }

        List<TextSubmission> textSubmissionList = participationService.findByExerciseIdWithEagerSubmittedSubmissionsWithoutResults(textExercise.getId()).parallelStream()
                .map(StudentParticipation::findLatestTextSubmission).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
        if (textSubmissionList.isEmpty())
            return Optional.empty();
        TextSubmission best = textSubmissionList.get(0);

        HashMap<TextBlock, Double> smallerClusterMap = calculateSmallerClusterPercentageBatch(textSubmissionList);
        double bestInformationGain = calculateInformationGain(best, smallerClusterMap);
        for (TextSubmission textSubmission : textSubmissionList) {
            if (bestInformationGain < calculateInformationGain(textSubmission, smallerClusterMap)) {
                bestInformationGain = calculateInformationGain(textSubmission, smallerClusterMap);
                best = textSubmission;
            }
        }
        return Optional.of(best);
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
            if (textBlock.isAssessable()) {
                continue;
            }
            if (textBlock.getCluster() == null) {
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
        return Arrays.stream(distanceMatrix[blockID]).map(distance -> 1.0 - distance).sum();
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
     *
     * @param textSubmissionList for which the smaller clusters should be calculated
     * @throws IllegalArgumentException if not all TextSubmissions are from the same exercise
     * @return return a HashMap where the textBlock is the key and smaller cluster percentage is the value
     * If a textBlock has no cluster or is already assessable, it isn't in the HashMap
     */
    private HashMap<TextBlock, Double> calculateSmallerClusterPercentageBatch(List<TextSubmission> textSubmissionList) {
        HashMap<TextBlock, Double> result = new HashMap<>();
        if (textSubmissionList.isEmpty()) {
            return result;
        }
        TextExercise currentExercise = (TextExercise) textSubmissionList.get(0).getParticipation().getExercise();
        List<TextCluster> clusters = textClusterRepository.findAllByExercise(currentExercise);

        if (!textSubmissionList.stream().map(Submission::getParticipation).map(Participation::getExercise).allMatch(elem -> elem == currentExercise)) {
            throw new IllegalArgumentException("All TextSubmissions have to be from the same Exercise");
        }
        textSubmissionList.forEach(textSubmission -> {
            textSubmission.getBlocks().forEach(textBlock -> {
                if (textBlock.getCluster() == null) {
                    return;
                }
                int smallerClusterCount = clusters.parallelStream().mapToInt(TextCluster::openTextBlockCount).reduce(0, (sum, elem) -> {
                    if (elem <= textBlock.getCluster().openTextBlockCount()) {
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
