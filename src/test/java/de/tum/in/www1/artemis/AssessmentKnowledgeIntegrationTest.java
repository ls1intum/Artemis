package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ModelAssesmentKnowledgeRepository;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.TextAssesmentKnowledgeRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.util.ModelingExerciseUtilService;

public class AssessmentKnowledgeIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private TextAssesmentKnowledgeRepository textAssesmentKnowledgeRepository;

    @Autowired
    private ModelAssesmentKnowledgeRepository modelAssesmentKnowledgeRepository;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 1, 0, 1);
        database.addInstructor("other-instructors", "instructorother");
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    /**
     * Tests that a new TextAssessmentKnowledge is created when we create an exercise from scratch
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCreateTextAssessmentKnowledgeIfExerciseIsCreatedFromScratch() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        int count = textAssesmentKnowledgeRepository.findAll().size();
        textExercise.setId(null);
        request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(count + 1).isEqualTo(textAssesmentKnowledgeRepository.findAll().size());
    }

    /**
     * Tests that TextAssessmentKnowledge is reused when we import an exercise
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testReuseTextAssessmentKnowledgeIfExerciseIsImported() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        int exerciseCount = textExerciseRepository.findAll().size();
        int textAssessmentKnowledgeCount = textAssesmentKnowledgeRepository.findAll().size();
        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(textAssesmentKnowledgeRepository.findAll().size()).isEqualTo(textAssessmentKnowledgeCount);
        assertThat(textExerciseRepository.findAll().size()).isEqualTo(exerciseCount + 1);
    }

    /**
     * Tests that TextAssessmentKnowledge is maintained on the DB even after deleting
     * the parent exercise if there are other exercises using it
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testKeepKnowledgeWhenExerciseIsDeletedIfOtherExercisesUseIt() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
        int exerciseCount = textExerciseRepository.findAll().size();
        int textAssessmentKnowledgeCount = textAssesmentKnowledgeRepository.findAll().size();
        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        assertThat(textExerciseRepository.findAll().size()).isEqualTo(exerciseCount - 1);
        assertThat(textAssesmentKnowledgeRepository.findAll().size()).isEqualTo(textAssessmentKnowledgeCount);
    }

    /**
     * Tests that a TextAssessmentKnowledge is deleted when we delete an exercise and
     * no other exercises use it
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteKnowledgeWhenExerciseIsDeletedIfNoOtherExercisesUseIt() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        TextExercise importedExercise = request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
        int exerciseCount = textExerciseRepository.findAll().size();
        int textAssessmentKnowledgeCount = textAssesmentKnowledgeRepository.findAll().size();
        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        int exerciseCountAfterDeletion = textExerciseRepository.findAll().size();
        int textAssessmentKnowledgeCountAfterDeletion = textAssesmentKnowledgeRepository.findAll().size();
        request.delete("/api/text-exercises/" + importedExercise.getId(), HttpStatus.OK);
        assertThat(exerciseCountAfterDeletion).isEqualTo(exerciseCount - 1);
        assertThat(textAssessmentKnowledgeCountAfterDeletion).isEqualTo(textAssessmentKnowledgeCount);
        assertThat(textExerciseRepository.findAll().size()).isEqualTo(exerciseCount - 2);
        assertThat(textAssesmentKnowledgeRepository.findAll().size()).isEqualTo(textAssessmentKnowledgeCount - 1);
    }

    /**
     * Tests that a new ModelAssessmentKnowledge is created when we create an exercise from scratch
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCreateModelAssessmentKnowledgeIfExerciseIsCreatedFromScratch() throws Exception {
        Course course = database.addEmptyCourse();
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(course.getId());
        int count = modelAssesmentKnowledgeRepository.findAll().size();
        request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(modelAssesmentKnowledgeRepository.findAll().size()).isEqualTo(count + 1);
    }

    /**
     * Tests that ModelAssessmentKnowledge is reused when we import an exercise
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testReuseModelAssessmentKnowledgeIfExerciseIsImported() throws Exception {
        final Course course = database.addCourseWithOneReleasedModelExerciseWithKnowledge();
        ModelingExercise modelingExercise = modelingExerciseRepository.findByCourseId(course.getId()).get(0);
        int exerciseCount = modelingExerciseRepository.findAll().size();
        int modelAssessmentKnowledgeCount = modelAssesmentKnowledgeRepository.findAll().size();
        request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(modelAssesmentKnowledgeRepository.findAll().size()).isEqualTo(modelAssessmentKnowledgeCount);
        assertThat(modelingExerciseRepository.findAll().size()).isEqualTo(exerciseCount + 1);
    }

    /**
     * Tests that ModelAssessmentKnowledge is not removed from the DB even after deleting
     * the parent exercise if there are other exercises using it
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testKeepModelAssessmentKnowledgeWhenExerciseIsDeletedIfOtherExercisesUseIt() throws Exception {
        final Course course = database.addCourseWithOneReleasedModelExerciseWithKnowledge();
        ModelingExercise modelingExercise = modelingExerciseRepository.findByCourseId(course.getId()).get(0);
        request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        int exerciseCount = modelingExerciseRepository.findAll().size();
        int modelAssessmentKnowledgeCount = modelAssesmentKnowledgeRepository.findAll().size();
        request.delete("/api/modeling-exercises/" + modelingExercise.getId(), HttpStatus.OK);
        assertThat(modelingExerciseRepository.findAll().size()).isEqualTo(exerciseCount - 1);
        assertThat(modelAssesmentKnowledgeRepository.findAll().size()).isEqualTo(modelAssessmentKnowledgeCount);
    }

    /**
     * Tests that a ModelAssessmentKnowledge is deleted when we delete an exercise and
     * no other exercises use it
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteModelAssessmentKnowledgeWhenExerciseIsDeletedIfNoOtherExercisesUseIt() throws Exception {
        final Course course = database.addCourseWithOneReleasedModelExerciseWithKnowledge();
        ModelingExercise modelingExercise = modelingExerciseRepository.findByCourseId(course.getId()).get(0);
        ModelingExercise importedExercise = request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class,
                HttpStatus.CREATED);
        int exerciseCount = modelingExerciseRepository.findAll().size();
        int modelAssessmentKnowledgeCount = modelAssesmentKnowledgeRepository.findAll().size();
        request.delete("/api/modeling-exercises/" + modelingExercise.getId(), HttpStatus.OK);
        int exerciseCountAfterDeletion = modelingExerciseRepository.findAll().size();
        int modelAssessmentKnowledgeCountAfterDeletion = modelAssesmentKnowledgeRepository.findAll().size();
        request.delete("/api/modeling-exercises/" + importedExercise.getId(), HttpStatus.OK);
        assertThat(exerciseCountAfterDeletion).isEqualTo(exerciseCount - 1);
        assertThat(modelAssessmentKnowledgeCountAfterDeletion).isEqualTo(modelAssessmentKnowledgeCount);
        assertThat(modelingExerciseRepository.findAll().size()).isEqualTo(exerciseCount - 2);
        assertThat(modelAssesmentKnowledgeRepository.findAll().size()).isEqualTo(modelAssessmentKnowledgeCount - 1);
    }
}
