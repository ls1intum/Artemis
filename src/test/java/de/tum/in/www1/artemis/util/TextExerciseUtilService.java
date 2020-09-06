package de.tum.in.www1.artemis.util;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.text.*;
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

    public ArrayList<TextBlock> generateTextBlocksWithTreeId(int count) {
        ArrayList<TextBlock> textBlocks = new ArrayList<>();
        TextBlock textBlock;
        for (int i = 0; i < count; i++) {
            textBlock = new TextBlock();
            textBlock.setText("TextBlock" + i);
            textBlock.setTreeId(i);
            textBlocks.add(textBlock);
        }
        return textBlocks;
    }

    public List<TextTreeNode> generateClusterTree(List<Integer> blockTreeIds) {
        List<TextTreeNode> clusterTree = new ArrayList<>();
        // Create and add root node
        TextTreeNode rootNode = new TextTreeNode();
        rootNode.setChildSize(blockTreeIds.size());
        rootNode.setChild(blockTreeIds.size());
        rootNode.setParent(-1);
        rootNode.setLambdaVal(-1);

        clusterTree.add(rootNode);
        clusterTree = treeGenerationRecursiveHelper(blockTreeIds, clusterTree, rootNode.getChild());
        return clusterTree.stream().sorted(Comparator.comparingLong(TextTreeNode::getChild)).collect(Collectors.toList());
    }

    /**
     * Recursively build a valid cluster tree
     * @param partialList - Subset of block ids to be considered in this step
     * @param clusterTree - Current cluster tree before this step
     * @param parentId - Id of the parent of the nodes to be created in this step
     * @return - Intermediate result as the current cluster tree after execution
     */
    private List<TextTreeNode> treeGenerationRecursiveHelper(List<Integer> partialList, List<TextTreeNode> clusterTree, long parentId) {
        double lambdaVal = random.nextDouble() + 2.5;
        if (partialList.size() <= 3) {
            // Create 2 block nodes with the same lambda value
            TextTreeNode child1 = new TextTreeNode();
            child1.setChildSize(1);
            child1.setChild(partialList.get(0));
            child1.setParent(parentId);
            child1.setLambdaVal(lambdaVal);

            TextTreeNode child2 = new TextTreeNode();
            child2.setChildSize(1);
            child2.setChild(partialList.get(1));
            child2.setParent(parentId);
            child2.setLambdaVal(lambdaVal);

            // Add new nodes to the head of the tree
            clusterTree.add(0, child1);
            clusterTree.add(0, child2);

            if(partialList.size() == 3) {
                // Create a block node and add to the head of the tree
                TextTreeNode child3 = new TextTreeNode();
                child3.setChildSize(1);
                child3.setChild(partialList.get(2));
                child3.setParent(parentId);
                child3.setLambdaVal(random.nextDouble() + 2.5);
                clusterTree.add(0, child3);
            }
        } else {
            // Create 2 cluster nodes with the same lambda value and add them to the tree
            List<Integer> subList1 = partialList.subList(0, partialList.size() / 2);
            long childId1 = clusterTree.get(clusterTree.size() - 1).getChild() + 1;
            long childId2 = clusterTree.get(clusterTree.size() - 1).getChild() + 2;

            TextTreeNode child1 = new TextTreeNode();
            child1.setChildSize(subList1.size());
            child1.setChild(childId1);
            child1.setParent(parentId);
            child1.setLambdaVal(lambdaVal);

            List<Integer> subList2 = partialList.subList(partialList.size() / 2, partialList.size());
            TextTreeNode child2 = new TextTreeNode();
            child2.setChildSize(subList2.size());
            child2.setChild(childId2);
            child2.setParent(parentId);
            child2.setLambdaVal(lambdaVal);

            clusterTree.add(child1);
            clusterTree.add(child2);
            // Increase granularity after adding new nodes
            treeGenerationRecursiveHelper(subList1, clusterTree, childId1);
            treeGenerationRecursiveHelper(subList2, clusterTree, childId2);
        }
        return clusterTree;
    }

    /**
     * Creates a flat partitioning out of the given cluster tree with given number of clusters.
     * @param clusterTree - A valid cluster tree
     * @param numberOfClusters - Expected number of clusters
     * @return treeIds of the clusters
     */
    public long[] getTreeIdsOfClustersInFlatPartitioning(List<TextTreeNode> clusterTree, int numberOfClusters) {
        long[] clusterTreeIds = new long[numberOfClusters];
        List<TextTreeNode> clustersOnly = clusterTree.stream().filter(x -> !x.isBlockNode()).collect(Collectors.toList());
        // Initialize as a list with the root node only
        List<TextTreeNode> partitioning = clustersOnly.stream().filter(x -> x.getParent() == -1).collect(Collectors.toList());
        if (partitioning.isEmpty()) {
            throw new IllegalArgumentException("Invalid cluster tree! No root node found.");
        }
        while (partitioning.size() < numberOfClusters) {
            Optional<TextTreeNode> toRemove = partitioning.stream().filter(x -> x.getChildSize() > 3).findFirst();
            if (toRemove.isEmpty()) {
                throw new IllegalArgumentException("Max possible number of clusters in this cluster tree: " + partitioning.size());
            }
            partitioning.remove(toRemove.get());
            partitioning.addAll(clustersOnly.stream().filter(x -> x.getParent() == toRemove.get().getChild()).collect(Collectors.toList()));
        }
        for (int i = 0; i < clusterTreeIds.length; i++) {
            clusterTreeIds[i] = partitioning.get(i).getChild();
        }
        return clusterTreeIds;
    }

    /**
     * Adds text blocks to clusters randomly. Cluster Tree is not initialized!
     * (Clusters do not have treeIds)
     * @param textBlocks - Text blocks to be added to clusters
     * @param clusterSizes - Number of text blocks to be added to each cluster
     * @param textExercise - Text exercise of the clusters and text blocks
     * @return - List of created clusters
     */
    public List<TextCluster> addTextBlocksToClustersWithoutTreeStructure(List<TextBlock> textBlocks, int[] clusterSizes, TextExercise textExercise) {

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

    /**
     * Adds text blocks to clusters based on the cluster tree.
     * @param textBlocks - All text blocks in the cluster tree
     * @param clusterTree - List of TextTreeNodes representing the cluster hierarchy
     * @param textExercise - Text exercise of the clusters and text blocks
     * @param treeIds - Array of treeIds of the clusters
     * @return - List of created clusters
     */
    public List<TextCluster> addTextBlocksToClustersWithTreeStructure(List<TextBlock> textBlocks, List<TextTreeNode> clusterTree, TextExercise textExercise, long[] treeIds) {
        if (clusterTree.stream().filter(TextTreeNode::isBlockNode).count() != textBlocks.size()) {
            throw new IllegalArgumentException("Number of block nodes in the tree has to be equal to the size of textBlocks");
        }
        if (textBlocks.stream().anyMatch(x -> x.getTreeId() == null)) {
            throw new IllegalArgumentException("All textBlocks must have a non-null treeId");
        }

        // Key: treeId, Value: Corresponding cluster
        Map<Long, TextCluster> clusterMap = new HashMap<>();
        // Create clusters
        for (int i = 0; i < treeIds.length; i++) {
            TextCluster cluster = new TextCluster().exercise(textExercise);
            cluster.setTreeId(treeIds[i]);
            clusterMap.put(treeIds[i], cluster);
        }
        // Add each textBlock to its cluster
        clusterMap.values().forEach(cluster -> {
            List<TextTreeNode> allChildren = clusterTree.stream().filter(x -> x.getParent() == cluster.getTreeId()).collect(Collectors.toList());
            List<TextTreeNode> clusterChildren = allChildren.stream().filter(x -> !x.isBlockNode()).collect(Collectors.toList());
            // Move downwards in the tree until we have all the blocks
            while (!clusterChildren.isEmpty()) {
                allChildren.removeAll(clusterChildren);
                for (TextTreeNode removed: clusterChildren) {
                    allChildren.addAll(clusterTree.stream().filter(x -> x.getParent() == removed.getChild()).collect(Collectors.toList()));
                }
                clusterChildren = allChildren.stream().filter(x -> !x.isBlockNode()).collect(Collectors.toList());
            }
            List<TextBlock> blocksInCluster = textBlocks.stream().filter(x -> allChildren.stream().anyMatch(y -> y.getChild() == x.getTreeId())).collect(Collectors.toList());
            clusterMap.get(cluster.getTreeId()).setBlocks(blocksInCluster);
        });
        return new ArrayList<>(clusterMap.values());
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
