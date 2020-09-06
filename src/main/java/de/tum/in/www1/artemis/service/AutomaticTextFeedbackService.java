package de.tum.in.www1.artemis.service;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import java.util.*;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.text.*;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.text.TextBlock;
import de.tum.in.www1.artemis.domain.text.TextCluster;
import de.tum.in.www1.artemis.domain.text.TextSubmission;
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
            } else if (block.getTreeId() != null) {
                // If block has no tree id, it means that it was created manually after the initial clustering
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
        // currentDist = distance between block and the node representing its cluster
        double currentDist = 0;
        TextTreeNode temp = clusterTree[block.getTreeId()];
        while(temp.getChild() != currentNode.getChild()) {
            currentDist += 1 / temp.getLambdaVal();
            temp = clusterTree[(int) temp.getParent()];
        }
        while(currentDist <= LAMBDA_THRESHOLD) {
            // parent is not an actual TextCluster but a "cluster of (clusters of) TextClusters"
            long parentId = currentNode.getParent();
            List<TextTreeNode> siblings = treeNodeRepository.findAllByParentAndExercise(parentId, exercise);
            siblings.remove(currentNode);
            siblings.sort(Comparator.comparingDouble(TextTreeNode::getLambdaVal).reversed());
            // In the following loop, we trace all ancestors within the threshold of the siblings for feedback
            while(!siblings.isEmpty()) {
                TextTreeNode x = siblings.remove(0);
                // If already above lambda threshold or a block node remove x directly
                if(1 / x.getLambdaVal() + currentDist > LAMBDA_THRESHOLD) {
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
                    } else {
                        for(TextTreeNode y : treeNodeRepository.findAllByParentAndExercise(x.getChild(), exercise)) {
                            y.setLambdaVal(sumLambdaValues(y.getLambdaVal(), x.getLambdaVal()));
                            siblings.add(y);
                        }
                        siblings.sort(Comparator.comparingDouble(TextTreeNode::getLambdaVal).reversed());
                    }
                }
            }
            // Give up when there is no level more to expand
            if(parentId == -1) {
                return Optional.empty();
            }
            currentDist = currentDist + 1 / currentNode.getLambdaVal();
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
            distances.put(i, getDistanceBetweenBlocks(cluster.getExercise(), i, j));
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

    /**
     * Gets the distance between two given text blocks
     * @param exercise - Exercise of the text blocks
     * @param i - Text block i
     * @param j - Text block j
     * @return the distance
     */
    private double getDistanceBetweenBlocks(TextExercise exercise, long i, long j) {
        if(i == j) {
            return 0;
        } else if(i < j) {
            return pairwiseDistanceRepository.findByExerciseAndAndBlockIAndBlockJ(exercise, i, j).getDistance();
        } else {
            return pairwiseDistanceRepository.findByExerciseAndAndBlockIAndBlockJ(exercise, j, i).getDistance();
        }
    }

}
