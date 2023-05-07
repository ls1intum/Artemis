package de.tum.in.www1.artemis.assessment;

import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.ase.athene.protobuf.Segment;
import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.modeling.ModelCluster;
import de.tum.in.www1.artemis.domain.modeling.ModelElement;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ModelAssessmentKnowledgeService;
import de.tum.in.www1.artemis.service.compass.controller.ModelClusterFactory;
import de.tum.in.www1.artemis.service.connectors.athene.AtheneService;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.ModelingExerciseUtilService;

class AssessmentKnowledgeIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "assessmentknowledge";

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private TextAssessmentKnowledgeRepository textAssessmentKnowledgeRepository;

    @Autowired
    private ModelAssessmentKnowledgeRepository modelAssessmentKnowledgeRepository;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private StudentParticipationRepository participationRepository;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private AtheneService atheneService;

    @Autowired
    private ModelAssessmentKnowledgeService modelAssessmentKnowledgeService;

    private static final int NUMBER_OF_STUDENTS = 2;

    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 0, 1);
        database.addInstructor("other-instructors", "instructorother");
    }

    /**
     * Tests that a new TextAssessmentKnowledge is created when we create an exercise from scratch
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTextAssessmentKnowledgeIfExerciseIsCreatedFromScratch() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        long count = textAssessmentKnowledgeRepository.count();
        textExercise.setId(null);
        request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(count + 1).isEqualTo(textAssessmentKnowledgeRepository.count());
    }

    /**
     * Tests that TextAssessmentKnowledge is reused when we import an exercise
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReuseTextAssessmentKnowledgeIfExerciseIsImported() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        long exerciseCount = textExerciseRepository.count();
        long count = textAssessmentKnowledgeRepository.count();
        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(count).isEqualTo(textAssessmentKnowledgeRepository.count());
        assertThat(exerciseCount + 1).isEqualTo(textExerciseRepository.count());
    }

    /**
     * Tests that TextAssessmentKnowledge is maintained on the DB even after deleting
     * the parent exercise if there are other exercises using it
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testKeepKnowledgeWhenExerciseIsDeletedIfOtherExercisesUseIt() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
        long exerciseCount = textExerciseRepository.count();
        long textAssessmentKnowledgeCount = textAssessmentKnowledgeRepository.count();
        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        assertThat(textExerciseRepository.count()).isEqualTo(exerciseCount - 1);
        assertThat(textAssessmentKnowledgeRepository.count()).isEqualTo(textAssessmentKnowledgeCount);
    }

    /**
     * Tests that a TextAssessmentKnowledge is deleted when we delete an exercise and
     * no other exercises use it
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteKnowledgeWhenExerciseIsDeletedIfNoOtherExercisesUseIt() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextExercise importedExercise = request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
        long exerciseCount = textExerciseRepository.count();
        long textAssessmentKnowledgeCount = textAssessmentKnowledgeRepository.count();
        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        long exerciseCountAfterDeletion = textExerciseRepository.count();
        long textAssessmentKnowledgeCountAfterDeletion = textAssessmentKnowledgeRepository.count();
        request.delete("/api/text-exercises/" + importedExercise.getId(), HttpStatus.OK);
        assertThat(exerciseCountAfterDeletion).isEqualTo(exerciseCount - 1);
        assertThat(textAssessmentKnowledgeCountAfterDeletion).isEqualTo(textAssessmentKnowledgeCount);
        assertThat(textExerciseRepository.count()).isEqualTo(exerciseCount - 2);
        assertThat(textAssessmentKnowledgeRepository.count()).isEqualTo(textAssessmentKnowledgeCount - 1);
    }

    /**
     * Tests that a new ModelAssessmentKnowledge is created when we create an exercise from scratch
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateModelAssessmentKnowledgeIfExerciseIsCreatedFromScratch() throws Exception {
        Course course = database.addEmptyCourse();
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(course.getId());
        long count = modelAssessmentKnowledgeRepository.count();
        request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(modelAssessmentKnowledgeRepository.count()).isEqualTo(count + 1);
    }

    /**
     * Tests that ModelAssessmentKnowledge is reused when we import an exercise
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReuseModelAssessmentKnowledgeIfExerciseIsImported() throws Exception {
        final Course course = database.addCourseWithOneReleasedModelExerciseWithKnowledge();
        ModelingExercise modelingExercise = modelingExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        long exerciseCount = modelingExerciseRepository.count();
        long modelAssessmentKnowledgeCount = modelAssessmentKnowledgeRepository.count();
        request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(modelAssessmentKnowledgeRepository.count()).isEqualTo(modelAssessmentKnowledgeCount);
        assertThat(modelingExerciseRepository.count()).isEqualTo(exerciseCount + 1);
    }

    /**
     * Tests that ModelAssessmentKnowledge is not removed from the DB even after deleting
     * the parent exercise if there are other exercises using it
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testKeepModelAssessmentKnowledgeWhenExerciseIsDeletedIfOtherExercisesUseIt() throws Exception {
        final Course course = database.addCourseWithOneReleasedModelExerciseWithKnowledge();
        ModelingExercise modelingExercise = modelingExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        long exerciseCount = modelingExerciseRepository.count();
        long modelAssessmentKnowledgeCount = modelAssessmentKnowledgeRepository.count();
        request.delete("/api/modeling-exercises/" + modelingExercise.getId(), HttpStatus.OK);
        assertThat(modelingExerciseRepository.count()).isEqualTo(exerciseCount - 1);
        assertThat(modelAssessmentKnowledgeRepository.count()).isEqualTo(modelAssessmentKnowledgeCount);
    }

    /**
     * Tests that a ModelAssessmentKnowledge is deleted when we delete an exercise and
     * no other exercises use it
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteModelAssessmentKnowledgeWhenExerciseIsDeletedIfNoOtherExercisesUseIt() throws Exception {
        final Course course = database.addCourseWithOneReleasedModelExerciseWithKnowledge();
        ModelingExercise modelingExercise = modelingExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        ModelingExercise importedExercise = request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class,
                HttpStatus.CREATED);
        long exerciseCount = modelingExerciseRepository.count();
        long modelAssessmentKnowledgeCount = modelAssessmentKnowledgeRepository.count();
        request.delete("/api/modeling-exercises/" + modelingExercise.getId(), HttpStatus.OK);
        long exerciseCountAfterDeletion = modelingExerciseRepository.count();
        long modelAssessmentKnowledgeCountAfterDeletion = modelAssessmentKnowledgeRepository.count();
        request.delete("/api/modeling-exercises/" + importedExercise.getId(), HttpStatus.OK);
        assertThat(exerciseCountAfterDeletion).isEqualTo(exerciseCount - 1);
        assertThat(modelAssessmentKnowledgeCountAfterDeletion).isEqualTo(modelAssessmentKnowledgeCount);
        assertThat(modelingExerciseRepository.count()).isEqualTo(exerciseCount - 2);
        assertThat(modelAssessmentKnowledgeRepository.count()).isEqualTo(modelAssessmentKnowledgeCount - 1);
    }

    /**
     * Tests that a TextAssessmentKnowledge is correctly set to text blocks
     * based on the TextAssessmentKnowledge of the respective exercise
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSetTextAssessmentKnowledgeToTextBlocks() {
        final Course course1 = database.addCourseWithOneReleasedTextExercise();
        final Course course2 = database.addCourseWithOneReleasedTextExercise();
        TextExercise exercise1 = (TextExercise) course1.getExercises().iterator().next();
        TextExercise exercise2 = (TextExercise) course2.getExercises().iterator().next();

        var textSubmissions1 = ModelFactory.generateTextSubmissions(NUMBER_OF_STUDENTS);
        var textSubmissions2 = ModelFactory.generateTextSubmissions(NUMBER_OF_STUDENTS);
        for (var i = 0; i < NUMBER_OF_STUDENTS; i++) {
            var textSubmission1 = textSubmissions1.get(i);
            var textSubmission2 = textSubmissions2.get(i);
            textSubmission1.setId(database.generateTempId());
            textSubmission2.setId(database.generateTempId());
            var student = database.getUserByLogin(TEST_PREFIX + "student" + (i + 1));
            var participation1 = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise1, student);
            var participation2 = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise2, student);
            participation1 = participationRepository.save(participation1);
            participation2 = participationRepository.save(participation2);
            textSubmission1.setParticipation(participation1);
            textSubmission2.setParticipation(participation2);
            textSubmission1.setSubmitted(true);
            textSubmission2.setSubmitted(true);
        }
        textSubmissionRepository.saveAll(textSubmissions1);
        textSubmissionRepository.saveAll(textSubmissions2);
        List<Segment> segments1 = textSubmissions1.stream().map(textSubmission -> {
            final String idString = textSubmission.getId() + ";0-30;" + textSubmission.getText().substring(0, 30);
            return Segment.newBuilder().setId(sha1Hex(idString)).setSubmissionId(textSubmission.getId().intValue()).setStartIndex(0).setEndIndex(30)
                    .setText(textSubmission.getText().substring(0, 30)).build();
        }).toList();
        List<Segment> segments2 = textSubmissions2.stream().map(textSubmission -> {
            final String idString = textSubmission.getId() + ";0-30;" + textSubmission.getText().substring(0, 30);
            return Segment.newBuilder().setId(sha1Hex(idString)).setSubmissionId(textSubmission.getId().intValue()).setStartIndex(0).setEndIndex(30)
                    .setText(textSubmission.getText().substring(0, 30)).build();
        }).toList();
        List<TextBlock> textBlocks1 = atheneService.parseTextBlocks(segments1, exercise1.getId());
        List<TextBlock> textBlocks2 = atheneService.parseTextBlocks(segments2, exercise1.getId());
        for (TextBlock textBlock : textBlocks1) {
            assertThat(textBlock.getKnowledge().getId()).isEqualTo(exercise1.getKnowledge().getId());
        }
        for (TextBlock textBlock : textBlocks2) {
            assertThat(textBlock.getKnowledge().getId()).isEqualTo(exercise2.getKnowledge().getId());
        }
        assertThat(exercise1.getKnowledge().getId()).isNotEqualTo(exercise2.getKnowledge().getId());
    }

    /**
     * Tests that a ModelAssessmentKnowledge is correctly set to model elements
     * based on the ModelAssessmentKnowledge of the respective exercise
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSetModelAssessmentKnowledgeToModelElements() throws Exception {
        ModelingSubmission submission1 = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        submission1.setId(1L);
        ModelingSubmission submission2 = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.cpy.json"), true);
        submission2.setId(2L);
        ModelingSubmission submission3 = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.cpy.json"), true);
        submission3.setId(3L);
        ModelingSubmission submission4 = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.cpy.json"), true);
        submission4.setId(4L);

        Course course = database.addEmptyCourse();
        ModelingExercise exercise1 = modelingExerciseUtilService.createModelingExercise(course.getId());
        ModelingExercise exercise2 = modelingExerciseUtilService.createModelingExercise(course.getId());
        modelAssessmentKnowledgeService = new ModelAssessmentKnowledgeService(modelAssessmentKnowledgeRepository, modelingExerciseRepository);
        exercise1.setKnowledge(modelAssessmentKnowledgeService.createNewKnowledge());
        exercise2.setKnowledge(modelAssessmentKnowledgeService.createNewKnowledge());

        ModelClusterFactory modelClusterFactory = new ModelClusterFactory();
        List<ModelCluster> modelClusters1 = modelClusterFactory.buildClusters(List.of(submission1, submission2), exercise1);
        List<ModelCluster> modelClusters2 = modelClusterFactory.buildClusters(List.of(submission3, submission4), exercise2);

        ModelCluster modelCluster = modelClusters1.get(0);
        for (ModelElement element : modelCluster.getModelElements()) {
            assertThat(element.getKnowledge().getId()).isEqualTo(exercise1.getKnowledge().getId());
        }

        modelCluster = modelClusters2.get(0);
        for (ModelElement element : modelCluster.getModelElements()) {
            assertThat(element.getKnowledge().getId()).isEqualTo(exercise2.getKnowledge().getId());
        }

        assertThat(exercise1.getKnowledge().getId()).isNotEqualTo(exercise2.getKnowledge().getId());
    }
}
