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
import de.tum.in.www1.artemis.repository.TextPairwiseDistanceRepository;
import de.tum.in.www1.artemis.repository.TextTreeNodeRepository;

@Service
@Profile("automaticText")
public class AutomaticTextFeedbackService {

    private final FeedbackService feedbackService;

    private static final double DISTANCE_THRESHOLD = 1;

    private static final double LAMBDA_THRESHOLD = 1.3967;

    private final TextBlockRepository textBlockRepository;

    private final TextTreeNodeRepository treeNodeRepository;

    private final TextPairwiseDistanceRepository pairwiseDistanceRepository;

    private final TextClusterRepository clusterRepository;

    public AutomaticTextFeedbackService(FeedbackService feedbackService, TextBlockRepository textBlockRepository,
                                        TextTreeNodeRepository treeNodeRepository, TextPairwiseDistanceRepository pairwiseDistanceRepository,
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
                final Optional<Feedback> feedbackOptional = findFeedbackForBlockInClusterWithoutFeedback(clusterTree, block, cluster, exercise);
                if(feedbackOptional.isPresent()) {
                    return feedbackOptional.get();
                }
            } else {
                final Optional<Feedback> feedbackOptional = findFeedbackForBlockWithoutCluster(clusterTree, block, exercise);
                if(feedbackOptional.isPresent()) {
                    feedbackOptional.get();
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
     * @return The feedback of a text block that fits as a candidate, if such a TextBlock is found
     */
    private Optional<Feedback> findFeedbackForBlockInClusterWithoutFeedback(TextTreeNode[] clusterTree, TextBlock block, TextCluster cluster, TextExercise exercise){
        TextTreeNode clusterNode = clusterTree[(int) cluster.getTreeId()];
        return traverseTreeForClusterNode(clusterTree, block, clusterNode, exercise);
    }

    /**
     * Look for feedback for a text block without a cluster, traversing the clusters in proximity
     * @param clusterTree - Tree structure of the cluster hierarchy
     * @param block - Text block which lacks feedback
     * @param exercise - text exercise of the text block
     * @return The feedback of a text block that fits as a candidate, if such a TextBlock is found
     */
    private Optional<Feedback> findFeedbackForBlockWithoutCluster(TextTreeNode[] clusterTree, TextBlock block, TextExercise exercise){
        TextTreeNode blockNode = clusterTree[block.getTreeId()];
        TextTreeNode parentNode = clusterTree[(int) blockNode.getParent()];
        return traverseTreeForClusterNode(clusterTree, block, parentNode, exercise);
    }

    private Optional<Feedback> traverseTreeForClusterNode(TextTreeNode[] clusterTree, TextBlock block, TextTreeNode clusterNode, TextExercise exercise){
        if(clusterNode.isBlockNode()) {
            return Optional.empty();
        }
        TextTreeNode currentNode = clusterNode;
        // Starting with dist = 1 / (lambda between block and cluster + lambda between cluster and parent)
        double currentDist = 1 / clusterTree[block.getTreeId()].getLambda_val();
        while(currentDist <= LAMBDA_THRESHOLD) {
            // parent is not an actual TextCluster but a "cluster of (clusters of) TextClusters"
            long parentId = currentNode.getParent();
            List<TextTreeNode> siblings = treeNodeRepository.findAllByParentAndExercise(parentId, exercise);
            siblings.sort(Comparator.comparingDouble(TextTreeNode::getLambda_val).reversed());
            // In the following loop, we trace all ancestors within the threshold of the siblings for feedback
            while(!siblings.isEmpty()) {
                TextTreeNode x = siblings.remove(0);
                x.setLambda_val(sumLambdaValues(x.getLambda_val(), 1 / currentDist));
                // If already above lambda threshold or a block node remove x directly
                if(x.getLambda_val() > LAMBDA_THRESHOLD) {
                    continue;
                }
                if(!x.isBlockNode()) {
                    Optional<TextCluster> clusterOptional = clusterRepository.findByTreeIdAndExercise(x.getChild(), exercise);
                    if(clusterOptional.isPresent()) {
                        TextCluster currentCluster = clusterOptional.get();
                        final Map<String, Feedback> feedbackForTextExerciseInCluster = feedbackService.getFeedbackForTextExerciseInCluster(currentCluster);
                        if(!feedbackForTextExerciseInCluster.isEmpty()) {
                            return getFeedbackInCluster(currentCluster, block, feedbackForTextExerciseInCluster);
                        }
                    }
                    for(TextTreeNode y : treeNodeRepository.findAllByParentAndExercise(x.getChild(), exercise)) {
                        y.setLambda_val(sumLambdaValues(y.getLambda_val(), x.getLambda_val()));
                        siblings.add(y);
                    }
                    siblings.sort(Comparator.comparingDouble(TextTreeNode::getLambda_val).reversed());
                }
            }
            // Give up when there is no level more to expand
            if(parentId == -1) {
                return Optional.empty();
            }
            currentDist = currentDist + 1 / currentNode.getLambda_val();
            currentNode = clusterTree[(int) parentId];
        }
        return Optional.empty();
    }

    private Optional<Feedback> getFeedbackInCluster(TextCluster cluster, TextBlock block,  Map<String, Feedback> feedbackMap) {
        final List<TextBlock> allBlocksInCluster = cluster.getBlocks().parallelStream().filter(elem -> !elem.equals(block)).collect(toList());
        // K: treeId of text block in cluster, V: Distance to "block"
        Map<Integer, Double> distances = new HashMap<>();
        for (TextBlock b: allBlocksInCluster) {
            int i = b.getTreeId();
            long j = block.getTreeId();
            if(i <= j) {
                distances.put(i, pairwiseDistanceRepository.findByExerciseAndBlocks(cluster.getExercise().getId(), (long) i, j).getDistance());
            } else {
                distances.put(i, pairwiseDistanceRepository.findByExerciseAndBlocks(cluster.getExercise().getId(), j, (long) i).getDistance());
            }
        }

        final Optional<TextBlock> mostSimilarBlockInClusterWithFeedback = allBlocksInCluster.parallelStream()
            // Filter all other blocks in the cluster for those with Feedback
            .filter(element -> feedbackMap.containsKey(element.getId()))
            // Find the closest block
            .min(comparing(element -> distances.get(element.getTreeId())));

        if (mostSimilarBlockInClusterWithFeedback.isPresent()
            && distances.get(mostSimilarBlockInClusterWithFeedback.get().getTreeId()) < DISTANCE_THRESHOLD) {
            final Feedback similarFeedback = feedbackMap.get(mostSimilarBlockInClusterWithFeedback.get().getId());
            return Optional.of(new Feedback().reference(block.getId()).credits(similarFeedback.getCredits()).detailText(similarFeedback.getDetailText())
                .type(FeedbackType.AUTOMATIC));
        }
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
     * @return inverted sum of inverted lambda values
     */
    private double sumLambdaValues(double l1, double l2) {
        return 1 / (1 / l1 + 1 / l2);
    }

}
