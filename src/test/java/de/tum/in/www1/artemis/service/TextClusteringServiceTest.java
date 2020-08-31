package de.tum.in.www1.artemis.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.text.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.util.DatabaseUtilService;

import de.tum.in.www1.artemis.util.ModelFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TextClusteringServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    // Sentences taken from the book Object-Oriented Software Engineering by B. Bruegge and A. Dutoit
    private static final String[] blockText = {
        "The purpose of science is to describe and understand complex systems,",
        "such as a system of atoms, a society of human beings, or a solar system.",
        "Traditionally, a distinction is made between natural sciences and social sciences to distinguish between two major types of systems.",
        "The purpose of natural sciences is to understand nature and its subsystems.",
        "Natural sciences include, for example, biology, chemistry, physics, and paleontology.",
        "The purpose of the social sciences is to understand human beings.",
        "Social sciences include psychology and sociology.",
        "There is another type of system that we call an artificial system.",
        "Examples of artificial systems include the space shuttle, airline reservation systems, and stock trading systems.",
        "Herbert Simon coined the term sciences of the artificial to describe the sciences that deal with artificial systems [Simon, 1970].",
        "Whereas natural and social sciences have been around for centuries, the sciences of the artificial are recent.",
    };

    @Autowired
    TextExerciseRepository textExerciseRepository;

    @Autowired
    TextBlockRepository textBlockRepository;

    @Autowired
    TextClusterRepository textClusterRepository;

    @Autowired
    TextTreeNodeRepository textTreeNodeRepository;

    @Autowired
    TextPairwiseDistanceRepository textPairwiseDistanceRepository;

    @Autowired
    TextSubmissionRepository textSubmissionRepository;

    @Autowired
    ExerciseService exerciseService;

    /*
    @Autowired
    CourseService courseService;

    @Autowired
    ExamService examService;

    @Autowired
    ExerciseGroupService exerciseGroupService;
     */

    @Autowired
    DatabaseUtilService database;

    private List<TextExercise> exercises;
    private List<TextBlock> blocks = new ArrayList<>();
    private List<TextCluster> clusters = new ArrayList<>();
    private List<TextTreeNode> treeNodes;
    private List<TextPairwiseDistance> pairwiseDistances;
    private TextSubmission submission;


    @BeforeAll
    public void init() {
        SecurityUtils.setAuthorizationObject(); // TODO: Why do we need this
        database.addUsers(10, 0, 1);
        database.addCourseWithOneFinishedTextExercise();
        database.addCourseWithOneFinishedTextExercise();
        database.addCourseWithOneFinishedTextExercise();
        database.addCourseExamExerciseGroupWithOneTextExercise();
        database.addCourseExamExerciseGroupWithOneTextExercise();

        exercises = textExerciseRepository.findAll();

        // Initialize data for the main exercise
        TextExercise exercise = exercises.get(0);

        initializeBlocksAndSubmissions(exercise);
        initializeClusters(exercise);

        // Read pre-computed results from JSON
        try {
            treeNodes = parseClusterTree(exercise);
            pairwiseDistances = parsePairwiseDistances(exercise);
        } catch (ParseException | IOException e) {
            database.resetDatabase();
            fail("JSON files for clusterTree or pairwiseDistances not successfully read/parsed.");
        }

        textClusterRepository.saveAll(clusters);
        textBlockRepository.saveAll(blocks);
        textTreeNodeRepository.saveAll(treeNodes);
        textPairwiseDistanceRepository.saveAll(pairwiseDistances);
        textExerciseRepository.save(exercise);

        // Initialize data for the second exercise
        TextExercise exercise2 = exercises.get(1);
        submission = ModelFactory.generateTextSubmission(blockText[0], Language.ENGLISH, true);
        database.addTextSubmission(
            exercise2,
            submission,
            "student1"
        );
        TextBlock block = ModelFactory.generateTextBlock(0,1,"b1");
        block.setSubmission(submission);
        block.setTreeId(11);
        textBlockRepository.save(block);
        TextCluster cluster = new TextCluster().exercise(exercise2);
        textClusterRepository.save(cluster);
        TextTreeNode incorrectTreeNode = new TextTreeNode().exercise(exercise2);
        incorrectTreeNode.setChild(-1);
        textTreeNodeRepository.save(incorrectTreeNode);
        TextPairwiseDistance incorrectPairwiseDistance = new TextPairwiseDistance().exercise(exercise2);
        textPairwiseDistanceRepository.save(incorrectPairwiseDistance);
    }

    @AfterAll
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testUniqueProperties() {
        TextExercise exercise = exercises.get(0);
        // Only half of the matrix is stored in the database, as it is symmetrical.
        int matrixSize = 0;
        for(int i = 1; i < blocks.size(); i++) {
            matrixSize += i;
        }
        assertThat(pairwiseDistances, hasSize(matrixSize));

        // Getter and setter for lambda value tested
        TextTreeNode testNode = new TextTreeNode();
        testNode.setLambdaVal(Double.POSITIVE_INFINITY);
        assertThat(ReflectionTestUtils.getField(testNode, "lambdaVal"), equalTo(-1.));
        assertThat(testNode.getLambdaVal(), equalTo(Double.POSITIVE_INFINITY));
        // isBlockNode() tested
        testNode.setChildSize(1);
        assertThat(testNode.isBlockNode(), equalTo(true));
        testNode.setChildSize(2);
        assertThat(testNode.isBlockNode(), equalTo(false));

        // The following should hold for the root node:
        //      parent == -1
        //      child == blocks.size()
        //      lambdaVal == -1 (getLambdaVal should return POSITIVE_INFINITY)
        //      childSize == blocks.size()
        TextTreeNode rootNode = textTreeNodeRepository.findAllByParentAndExercise(-1L, exercise).get(0);
        assertThat((int) rootNode.getChild(), equalTo(blocks.size()));
        assertThat(rootNode.getLambdaVal(), equalTo(Double.POSITIVE_INFINITY));
        assertThat(rootNode.isBlockNode(), equalTo(false));
        assertThat((int) rootNode.getChildSize(), equalTo(blocks.size()));
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testStateAfterClustering() {

        TextExercise exercise = exercises.get(0);
        TextExercise exercise2 = exercises.get(1);

        TextBlock block1 = ModelFactory.generateTextBlock(0,1,"b1");
        block1.setSubmission(blocks.get(0).getSubmission());
        textBlockRepository.save(block1);

        TextBlock block2 = ModelFactory.generateTextBlock(0,1,"b2");
        block2.setTreeId(10);
        textBlockRepository.save(block2);

        List<TextBlock> currentBlocks = textBlockRepository.findAllBySubmission_Participation_Exercise_IdAndTreeIdNotNull(exercise.getId());
        assertThat(currentBlocks, hasSize(blocks.size()));
        assertThat(currentBlocks.contains(block1), equalTo(false));
        assertThat(currentBlocks.contains(block2), equalTo(false));
        textBlockRepository.delete(block1);
        textBlockRepository.delete(block2);

        TextCluster cluster = new TextCluster().exercise(exercise2);
        textClusterRepository.save(cluster);
        List<TextCluster> currentClusters = textClusterRepository.findAllByExercise(exercise);
        assertThat(currentClusters, hasSize(clusters.size()));
        assertThat(currentClusters.contains(cluster), equalTo(false));
        textClusterRepository.delete(cluster);

        TextTreeNode incorrectTreeNode = new TextTreeNode().exercise(exercise2);
        incorrectTreeNode.setChild(-2);
        textTreeNodeRepository.save(incorrectTreeNode);
        List<TextTreeNode> currentTreeNodes = textTreeNodeRepository.findAllByExercise(exercise);
        assertThat(currentTreeNodes, hasSize(treeNodes.size()));
        assertThat(currentTreeNodes.contains(incorrectTreeNode), equalTo(false));
        textTreeNodeRepository.delete(incorrectTreeNode);

        TextPairwiseDistance incorrectPairwiseDistance = new TextPairwiseDistance().exercise(exercise2);
        textPairwiseDistanceRepository.save(incorrectPairwiseDistance);
        List<TextPairwiseDistance> currentPairwiseDistances = textPairwiseDistanceRepository.findAllByExercise(exercise);
        assertThat(currentPairwiseDistances, hasSize(pairwiseDistances.size()));
        assertThat(currentPairwiseDistances.contains(incorrectPairwiseDistance), equalTo(false));
        textPairwiseDistanceRepository.delete(incorrectPairwiseDistance);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testExerciseRemoval() {
        TextExercise exercise = exercises.get(1);

        // Test cascading removals for exercise
        exerciseService.delete(exercise.getId(), true, true);
        assertThat(textBlockRepository.findAllBySubmission_Participation_Exercise_IdAndTreeIdNotNull(exercise.getId()), hasSize(0));
        assertThat(textSubmissionRepository.findById(submission.getId()), equalTo(Optional.empty()));
        assertThat(textExerciseRepository.findById(exercise.getId()).isPresent(), equalTo(false));
        assertThat(textClusterRepository.findAllByExercise(exercise), hasSize(0));
        assertThat(textTreeNodeRepository.findAllByExercise(exercise), hasSize(0));
        assertThat(textPairwiseDistanceRepository.findAllByExercise(exercise), hasSize(0));
    }

    /*
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCourseRemoval() {
        // Test cascading removals for course
        TextExercise exercise = exercises.get(2);

        TextTreeNode newNode = new TextTreeNode().exercise(exercise);
        newNode.setChild(111);
        TextPairwiseDistance newDist = new TextPairwiseDistance().exercise(exercise);
        textPairwiseDistanceRepository.save(newDist);
        textTreeNodeRepository.save(newNode);

        courseService.delete(exercise.getCourseViaExerciseGroupOrCourseMember());
        assertThat(textExerciseRepository.findById(exercise.getId()).isPresent(), equalTo(false));
        assertThat(textTreeNodeRepository.findAllByExercise(exercise), hasSize(0));
        assertThat(textPairwiseDistanceRepository.findAllByExercise(exercise), hasSize(0));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testExamRemoval() {
        // Test cascading removals for exam
        TextExercise exercise = exercises.get(3);

        TextTreeNode newNode = new TextTreeNode().exercise(exercise);
        newNode.setChild(112);
        TextPairwiseDistance newDist = new TextPairwiseDistance().exercise(exercise);
        textPairwiseDistanceRepository.save(newDist);
        textTreeNodeRepository.save(newNode);

        examService.delete(exercise.getExerciseGroup().getExam());
        assertThat(textExerciseRepository.findById(exercise.getId()).isPresent(), equalTo(false));
        assertThat(textTreeNodeRepository.findAllByExercise(exercise), hasSize(0));
        assertThat(textPairwiseDistanceRepository.findAllByExercise(exercise), hasSize(0));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testExerciseGroupRemoval() {
        // Test cascading removals for exercise group
        TextExercise exercise = exercises.get(4);

        TextTreeNode newNode = new TextTreeNode().exercise(exercise);
        newNode.setChild(113);
        newNode.setChildSize(0);
        newNode.setParent(112);
        newNode.setLambdaVal(-1);
        TextPairwiseDistance newDist = new TextPairwiseDistance().exercise(exercise);
        textPairwiseDistanceRepository.save(newDist);
        textTreeNodeRepository.save(newNode);

        exerciseGroupService.delete(exercise.getExerciseGroup().getId());
        assertThat(textExerciseRepository.findById(exercise.getId()).isPresent(), equalTo(false));
        assertThat(textTreeNodeRepository.findAllByExercise(exercise), hasSize(0));
        assertThat(textPairwiseDistanceRepository.findAllByExercise(exercise), hasSize(0));
    }
     */

    /**
     * Reads and parses the cluster tree from json file for given exercise
     * @param exercise
     * @return list of tree nodes
     * @throws IOException
     * @throws ParseException
     */
    @WithMockUser(value = "admin", roles = "ADMIN")
    private static List<TextTreeNode> parseClusterTree(TextExercise exercise) throws IOException, ParseException {
        List<TextTreeNode> result = new ArrayList<>();
        JSONParser jsonParser = new JSONParser();
        FileReader reader = new FileReader("src/test/resources/test-data/clustering/clusterTree.json");
        JSONArray treeList = (JSONArray) jsonParser.parse(reader);
        for(int i = 0; i < treeList.size(); i++) {
            JSONObject n = (JSONObject) treeList.get(i);
            TextTreeNode node = new TextTreeNode();
            node.setExercise(exercise);
            node.setParent((long) n.get("parent"));
            node.setLambdaVal((double) n.get("lambdaVal"));
            node.setChildSize((long) n.get("childSize"));
            node.setChild((long) n.get("child"));
            result.add(node);
        }
        return result;
    }

    /**
     * Reads and parses the pairwise distances from json file for given exercise
     * @param exercise
     * @return list of pairwise distances
     * @throws IOException
     * @throws ParseException
     */
    @WithMockUser(value = "admin", roles = "ADMIN")
    private static List<TextPairwiseDistance> parsePairwiseDistances(TextExercise exercise) throws IOException, ParseException {
        List<TextPairwiseDistance> result = new ArrayList<>();
        JSONParser jsonParser = new JSONParser();
        FileReader reader = new FileReader("src/test/resources/test-data/clustering/pairwiseDistances.json");
        JSONArray distList = (JSONArray) jsonParser.parse(reader);
        for(int i = 0; i < distList.size(); i++) {
            JSONObject d = (JSONObject) distList.get(i);
            TextPairwiseDistance dist = new TextPairwiseDistance();
            dist.setExercise(exercise);
            dist.setDistance((double) d.get("distance"));
            dist.setBlockI((long) d.get("blockI"));
            dist.setBlockJ((long) d.get("blockJ"));
            result.add(dist);
        }
        return result;
    }

    /**
     * Initializes TextBlocks and TextSubmissions from the static blockText array for given exercise
     * @param exercise
     */
    @WithMockUser(value = "admin", roles = "ADMIN")
    private void initializeBlocksAndSubmissions(TextExercise exercise) {
        // Create first submission, save text blocks and submission
        submission = ModelFactory.generateTextSubmission(blockText[0] + " " + blockText[1], Language.ENGLISH, true);
        database.addTextSubmission(
            exercise,
            submission,
            "student1"
        );

        TextBlock bl = new TextBlock().automatic().startIndex(0).endIndex(1).submission(submission).text(blockText[0]);
        bl.computeId();
        bl.setTreeId(0);
        blocks.add(bl);
        textBlockRepository.save(bl);
        submission.addBlock(bl);
        textSubmissionRepository.save(submission);

        bl = new TextBlock().automatic().startIndex(1).endIndex(2).submission(submission).text(blockText[1]);
        bl.computeId();
        bl.setTreeId(1);
        blocks.add(bl);
        textBlockRepository.save(bl);
        submission.addBlock(bl);
        textSubmissionRepository.save(submission);

        // Create submissions, save text blocks and submissions
        for(int i = 2; i <= 10; i++) {
            submission = ModelFactory.generateTextSubmission(blockText[i], Language.ENGLISH, true);
            database.addTextSubmission(
                exercise,
                submission,
                "student" + i
            );
            bl = new TextBlock().automatic().startIndex(0).endIndex(1).submission(submission).text(blockText[i]);
            bl.computeId();
            bl.setTreeId(i);
            blocks.add(bl);
            textBlockRepository.save(bl);
            submission.addBlock(bl);
            textSubmissionRepository.save(submission);
        }
    }

    /**
     * Initializes TextClusters for given exercise
     * @param exercise
     */
    @WithMockUser(value = "admin", roles = "ADMIN")
    private void initializeClusters(TextExercise exercise) {
        TextCluster cluster1 = new TextCluster().exercise(exercise);
        cluster1.addBlocks(blocks.get(1));
        cluster1.addBlocks(blocks.get(7));
        cluster1.setTreeId(13);
        blocks.add(1, blocks.remove(1).cluster(cluster1));
        blocks.add(7, blocks.remove(7).cluster(cluster1));

        TextCluster cluster2 = new TextCluster().exercise(exercise);
        cluster2.addBlocks(blocks.get(4));
        cluster2.addBlocks(blocks.get(6));
        cluster2.setTreeId(15);
        blocks.add(4, blocks.remove(4).cluster(cluster2));
        blocks.add(6, blocks.remove(6).cluster(cluster2));

        TextCluster cluster3 = new TextCluster().exercise(exercise);
        cluster3.addBlocks(blocks.get(2));
        cluster3.addBlocks(blocks.get(9));
        cluster3.addBlocks(blocks.get(10));
        cluster3.setTreeId(16);
        blocks.add(2, blocks.remove(2).cluster(cluster3));
        blocks.add(9, blocks.remove(9).cluster(cluster3));
        blocks.add(10, blocks.remove(10).cluster(cluster3));

        TextCluster cluster4 = new TextCluster().exercise(exercise);
        cluster4.addBlocks(blocks.get(0));
        cluster4.addBlocks(blocks.get(3));
        cluster4.addBlocks(blocks.get(5));
        cluster4.setTreeId(17);
        blocks.add(0, blocks.remove(0).cluster(cluster4));
        blocks.add(3, blocks.remove(3).cluster(cluster4));
        blocks.add(5, blocks.remove(5).cluster(cluster3));

        clusters.add(cluster1);
        clusters.add(cluster2);
        clusters.add(cluster3);
        clusters.add(cluster4);
    }
}
