package de.tum.in.www1.artemis.service;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import java.util.*;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.repository.TextClusterRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TreeNode;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.PairwiseDistanceRepository;
import de.tum.in.www1.artemis.repository.TreeNodeRepository;

@Service
@Profile("automaticText")
public class AutomaticTextFeedbackService {

    private final FeedbackService feedbackService;

    private static final double DISTANCE_THRESHOLD = 1;

    // TODO: Update this value later
    private static final double LAMBDA_THRESHOLD = 3;

    private final TextBlockRepository textBlockRepository;

    private final TreeNodeRepository treeNodeRepository;

    private final PairwiseDistanceRepository pairwiseDistanceRepository;

    private final TextClusterRepository clusterRepository;

    public AutomaticTextFeedbackService(FeedbackService feedbackService, TextBlockRepository textBlockRepository,
                                        TreeNodeRepository treeNodeRepository, PairwiseDistanceRepository pairwiseDistanceRepository,
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
            final TreeNode[] clusterTree = fetchClusterTreeForExercise(exercise);

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
                findFeedbackForBlockInClusterWithoutFeedback(clusterTree, block, cluster, exercise);
            } else {
                findFeedbackForBlockWithoutCluster(clusterTree, block, exercise);
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
     */
    private void findFeedbackForBlockInClusterWithoutFeedback(TreeNode[] clusterTree, TextBlock block, TextCluster cluster, TextExercise exercise){
        boolean feedbackFound = false;
        TreeNode currentNode = clusterTree[(int) cluster.getTreeId()];
        // Starting with lambda = (lambda between block and cluster) + (lambda between cluster and parent)
        double currentLambdaVal = addLambdaValues(clusterTree[block.getTreeId()].getLambda_val(), currentNode.getLambda_val());
        while(!feedbackFound && currentLambdaVal > LAMBDA_THRESHOLD) {
            // parent is not an actual TextCluster but a "cluster of (clusters of) TextClusters"
            long parentId = currentNode.getParent();
            List<TreeNode> siblings = treeNodeRepository.findAllByParentAndExercise(parentId, exercise);
            siblings.sort(Comparator.comparingDouble(TreeNode::getLambda_val));
            // In the following loop, we trace all ancestors within the threshold of the siblings for feedback
            while(!siblings.isEmpty()) {
                TreeNode x = siblings.remove(0);
                // If already below lambda threshold remove x directly
                if(addLambdaValues(x.getLambda_val(), currentLambdaVal) < LAMBDA_THRESHOLD) {
                    continue;
                }
                if(x.isBlockNode()) {
                    // TODO: Check if TextBlock has feedback, inherit it if present
                } else {
                    for(TreeNode y : treeNodeRepository.findAllByParentAndExercise(x.getChild(), exercise)) {
                        y.setLambda_val(addLambdaValues(y.getLambda_val(), x.getLambda_val()));
                        // If still above lambda threshold add y to siblings
                        if(addLambdaValues(y.getLambda_val(), currentLambdaVal) > LAMBDA_THRESHOLD) {
                            siblings.add(y);
                        }
                    }
                    siblings.sort(Comparator.comparingDouble(TreeNode::getLambda_val));
                }
            }
            currentNode = clusterTree[(int) parentId];
            currentLambdaVal = addLambdaValues(currentLambdaVal, currentNode.getLambda_val());
        }
    }

    /**
     * Look for feedback for a text block without a cluster, traversing the clusters in proximity
     * @param clusterTree - Tree structure of the cluster hierarchy
     * @param block - Text block which lacks feedback
     * @param exercise - text exercise of the text block
     */
    private void findFeedbackForBlockWithoutCluster(TreeNode[] clusterTree, TextBlock block, TextExercise exercise){
        //TODO: Implement
    }

    /**
     * Fetches the cluster tree from the database and places the elements in the correct positions in the array
     * (Index in array = treeNode.child)
     * @param exercise - Text exercise of the cluster tree
     * @return The cluster tree in form of an array
     */
    private TreeNode[] fetchClusterTreeForExercise(TextExercise exercise) {
        final List<TreeNode> unorderedTreeNodes = treeNodeRepository.findAllByExercise(exercise);
        final TreeNode[] clusterTree = new TreeNode[unorderedTreeNodes.size()];
        for (TreeNode node: unorderedTreeNodes) {
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
