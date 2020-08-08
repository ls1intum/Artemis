package de.tum.in.www1.artemis.service;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import java.util.*;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.text.*;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.PairwiseDistanceRepository;
import de.tum.in.www1.artemis.repository.TextTreeNodeRepository;

@Service
@Profile("automaticText")
public class AutomaticTextFeedbackService {

    private final FeedbackService feedbackService;

    private static final double DISTANCE_THRESHOLD = 1;

    // TODO: Update this value later
    private static final double LAMBDA_THRESHOLD = 3;

    private final TextBlockRepository textBlockRepository;

    private final TextTreeNodeRepository treeNodeRepository;

    private final PairwiseDistanceRepository pairwiseDistanceRepository;

    private final TextClusterRepository clusterRepository;

    public AutomaticTextFeedbackService(FeedbackService feedbackService, TextBlockRepository textBlockRepository,
                                        TextTreeNodeRepository treeNodeRepository, PairwiseDistanceRepository pairwiseDistanceRepository,
                                        TextClusterRepository clusterRepository) {
        this.feedbackService = feedbackService;
        this.textBlockRepository = textBlockRepository;
        this.treeNodeRepository = treeNodeRepository;
        this.pairwiseDistanceRepository = pairwiseDistanceRepository;
        this.clusterRepository = clusterRepository;
    }

    /**
     * Suggest Feedback for a Submission based on its cluster.
     * For each TextBlock of the submission, this method finds already existing Feedback elements in the same cluster and chooses the one with the minimum distance.
     * Otherwise, an empty Feedback Element is created for simplicity.
     * Feedbacks are stored inline with the provided Result object.
     *
     * @param result Result for the Submission
     */
    @Transactional(readOnly = true)
    public void suggestFeedback(@NotNull Result result) {
        final TextSubmission textSubmission = (TextSubmission) result.getSubmission();
        final List<TextBlock> blocks = textBlockRepository.findAllWithEagerClusterBySubmissionId(textSubmission.getId());
        textSubmission.setBlocks(blocks);

        final List<Feedback> suggestedFeedback = blocks.stream().map(block -> {
            final TextCluster cluster = block.getCluster();
            final TextExercise exercise = (TextExercise) block.getSubmission().getParticipation().getExercise();
            final TextTreeNode[] clusterTree = fetchClusterTreeForExercise(exercise);

            // if TextBlock is part of a cluster, we try to find an existing Feedback Element
            if (cluster != null) {
                // Find all Feedbacks for other Blocks in Cluster.
                final List<TextBlock> allBlocksInCluster = cluster.getBlocks().parallelStream().filter(elem -> !elem.equals(block)).collect(toList());
                final Map<String, Feedback> feedbackForTextExerciseInCluster = feedbackService.getFeedbackForTextExerciseInCluster(cluster);

                if (feedbackForTextExerciseInCluster.size() != 0) {
                    final Optional<TextBlock> mostSimilarBlockInClusterWithFeedback = allBlocksInCluster.parallelStream()

                            // Filter all other blocks in the cluster for those with Feedback
                            .filter(element -> feedbackForTextExerciseInCluster.containsKey(element.getId()))

                            // Find the closest block
                            .min(comparing(element -> cluster.distanceBetweenBlocks(block, element)));

                    if (mostSimilarBlockInClusterWithFeedback.isPresent()
                            && cluster.distanceBetweenBlocks(block, mostSimilarBlockInClusterWithFeedback.get()) < DISTANCE_THRESHOLD) {
                        final Feedback similarFeedback = feedbackForTextExerciseInCluster.get(mostSimilarBlockInClusterWithFeedback.get().getId());
                        return new Feedback().reference(block.getId()).credits(similarFeedback.getCredits()).detailText(similarFeedback.getDetailText())
                                .type(FeedbackType.AUTOMATIC);
                    }
                }
                final Optional<String> feedbackId = findFeedbackForBlockInClusterWithoutFeedback(clusterTree, block, cluster, exercise);
                if(feedbackId.isPresent()) {
                    // TODO: Return new feedback
                }
            } else {
                final Optional<String> feedbackId = findFeedbackForBlockWithoutCluster(clusterTree, block, exercise);
                if(feedbackId.isPresent()) {
                    // TODO: Return new feedback
                }
            }

            return null;
        }).filter(Objects::nonNull).collect(toList());

        result.setFeedbacks(suggestedFeedback);
    }

    /**
     * Look for feedback for a text block in a cluster without feedback in clusters in proximity
     * @param clusterTree - Tree structure of the cluster hierarchy
     * @param block - Text block
     * @param cluster - Text cluster which contains the text block and lacks feedback
     * @param exercise - Text exercise of the text block
     * @return The id of the text block, of which the feedback will be inherited, if such a TextBlock is found
     */
    private Optional<String> findFeedbackForBlockInClusterWithoutFeedback(TextTreeNode[] clusterTree, TextBlock block, TextCluster cluster, TextExercise exercise){
        boolean feedbackFound = false;
        TextTreeNode currentNode = clusterTree[(int) cluster.getTreeId()];
        // Starting with lambda = (lambda between block and cluster) + (lambda between cluster and parent)
        double currentLambdaVal = addLambdaValues(clusterTree[block.getTreeId()].getLambda_val(), currentNode.getLambda_val());
        while(!feedbackFound && currentLambdaVal > LAMBDA_THRESHOLD) {
            // parent is not an actual TextCluster but a "cluster of (clusters of) TextClusters"
            long parentId = currentNode.getParent();
            List<TextTreeNode> siblings = treeNodeRepository.findAllByParentAndExercise(parentId, exercise);
            siblings.sort(Comparator.comparingDouble(TextTreeNode::getLambda_val));
            // In the following loop, we trace all ancestors within the threshold of the siblings for feedback
            while(!siblings.isEmpty()) {
                TextTreeNode x = siblings.remove(0);
                // If already below lambda threshold remove x directly
                if(addLambdaValues(x.getLambda_val(), currentLambdaVal) < LAMBDA_THRESHOLD) {
                    continue;
                }
                if(x.isBlockNode()) {
                    // TODO: Check if TextBlock has feedback, inherit it if present
                } else {
                    for(TextTreeNode y : treeNodeRepository.findAllByParentAndExercise(x.getChild(), exercise)) {
                        y.setLambda_val(addLambdaValues(y.getLambda_val(), x.getLambda_val()));
                        // If still above lambda threshold add y to siblings
                        if(addLambdaValues(y.getLambda_val(), currentLambdaVal) > LAMBDA_THRESHOLD) {
                            siblings.add(y);
                        }
                    }
                    siblings.sort(Comparator.comparingDouble(TextTreeNode::getLambda_val));
                }
            }
            currentNode = clusterTree[(int) parentId];
            currentLambdaVal = addLambdaValues(currentLambdaVal, currentNode.getLambda_val());
        }
        return Optional.empty();
    }

    /**
     * Look for feedback for a text block without a cluster, traversing the clusters in proximity
     * @param clusterTree - Tree structure of the cluster hierarchy
     * @param block - Text block which lacks feedback
     * @param exercise - text exercise of the text block
     * @return The id of the text block, of which the feedback will be inherited, if such a TextBlock is found
     */
    private Optional<String> findFeedbackForBlockWithoutCluster(TextTreeNode[] clusterTree, TextBlock block, TextExercise exercise){
        TextTreeNode currentNode = clusterTree[(int) block.getTreeId()];
        Optional<TextCluster> clusterOptional = clusterRepository.findByTreeIdAndExercise(currentNode.getParent(), exercise);
        // There is an existing cluster one level above in tree
        if(clusterOptional.isPresent()) {
            TextCluster cluster = clusterOptional.get();
            final Map<String, Feedback> feedbackForTextExerciseInCluster = feedbackService.getFeedbackForTextExerciseInCluster(cluster);
            // If no feedback in cluster -> call findFeedbackForBlockInClusterWithoutFeedback for that cluster
            if(feedbackForTextExerciseInCluster.size() == 0) {
                return findFeedbackForBlockInClusterWithoutFeedback(clusterTree, block, cluster, exercise);
            } else {
                // Copied from method suggestFeedback()
                final List<TextBlock> allBlocksInCluster = cluster.getBlocks().parallelStream().filter(elem -> !elem.equals(block)).collect(toList());
                final Optional<TextBlock> mostSimilarBlockInClusterWithFeedback = allBlocksInCluster.parallelStream()
                    .filter(element -> feedbackForTextExerciseInCluster.containsKey(element.getId()))
                    .min(comparing(element -> cluster.distanceBetweenBlocks(block, element)));
                if (mostSimilarBlockInClusterWithFeedback.isPresent()
                    && cluster.distanceBetweenBlocks(block, mostSimilarBlockInClusterWithFeedback.get()) < DISTANCE_THRESHOLD) {
                    return Optional.of(mostSimilarBlockInClusterWithFeedback.get().getId());
                }
            }
        }
        // If no cluster one level above, return empty optional
        return Optional.empty();
    }

    /**
     * Fetches the cluster tree from the database and places the elements in the correct positions in the array
     * (Index in array = treeNode.child)
     * @param exercise - Text exercise of the cluster tree
     * @return The cluster tree in form of an array
     */
    private TextTreeNode[] fetchClusterTreeForExercise(TextExercise exercise) {
        final List<TextTreeNode> unorderedTreeNodes = treeNodeRepository.findAllByExercise(exercise);
        final TextTreeNode[] clusterTree = new TextTreeNode[unorderedTreeNodes.size()];
        for (TextTreeNode node: unorderedTreeNodes) {
            clusterTree[(int) node.getChild()] = node;
        }
        return clusterTree;
    }

    /**
     * Computes the sum of two lambda values (lambdaVal = 1 / distance)
     * @param l1 - first lambdaVal
     * @param l2 - second lambdaVal
     * @return sum of both values
     */
    private double addLambdaValues(double l1, double l2) {
        return 1 / ((1 / l1) + (1 / l2));
    }

}
