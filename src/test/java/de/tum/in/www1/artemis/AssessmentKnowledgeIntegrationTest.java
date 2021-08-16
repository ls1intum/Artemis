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
import de.tum.in.www1.artemis.repository.TextAssesmentKnowledgeRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;

public class AssessmentKnowledgeIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private TextAssesmentKnowledgeRepository textAssesmentKnowledgeRepository;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 1, 0, 1);
        database.addInstructor("other-instructors", "instructorother");
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createTextAssessmentKnowledgeIfExerciseIsCreatedFromScratch() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExerciseWithKnowledge();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        int count = textAssesmentKnowledgeRepository.findAll().size();
        textExercise.setId(null);
        request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(count + 1).isEqualTo(textAssesmentKnowledgeRepository.findAll().size());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void reuseTextAssessmentKnowledgeIfExerciseIsImported() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExerciseWithKnowledge();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        int exercise_count = textExerciseRepository.findAll().size();
        int textAssessmentKnowledgeCount = textAssesmentKnowledgeRepository.findAll().size();
        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(textAssesmentKnowledgeRepository.findAll().size()).isEqualTo(textAssessmentKnowledgeCount);
        assertThat(textExerciseRepository.findAll().size()).isEqualTo(exercise_count + 1);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void keepKnowledgeWhenExerciseIsDeletedIfOtherExercisesUseIt() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExerciseWithKnowledge();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
        int exercise_count = textExerciseRepository.findAll().size();
        int textAssessmentKnowledgeCount = textAssesmentKnowledgeRepository.findAll().size();
        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        assertThat(textExerciseRepository.findAll().size()).isEqualTo(exercise_count - 1);
        assertThat(textAssesmentKnowledgeRepository.findAll().size()).isEqualTo(textAssessmentKnowledgeCount);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void deleteKnowledgeWhenExerciseIsDeletedIfNoOtherExercisesUseIt() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExerciseWithKnowledge();
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
}
