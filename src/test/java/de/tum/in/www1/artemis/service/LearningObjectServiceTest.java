package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.StudentScoreUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.LearningObject;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.exercise.programming.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.lecture.LectureFactory;
import de.tum.in.www1.artemis.lecture.LectureUtilService;

class LearningObjectServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "learningobjectservice";

    @Autowired
    private LearningObjectService learningObjectService;

    @Autowired
    private StudentScoreUtilService studentScoreUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private User student;

    private Course course;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = { true, false })
    void testIsCompletedByUserExercise(boolean completed) {
        var programmingExercise = course.getExercises().stream().findFirst().get();
        studentScoreUtilService.createStudentScore(programmingExercise, student, completed ? 84.0 : 42.0);

        assertThat(learningObjectService.isCompletedByUser(programmingExercise, student)).isEqualTo(completed);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = { true, false })
    void testIsCompletedByUserLectureUnit(boolean completed) {
        var lectureUnit = LectureFactory.generateAttachmentUnit();

        if (completed) {
            lectureUtilService.completeLectureUnitForUser(lectureUnit, student);
        }

        assertThat(learningObjectService.isCompletedByUser(lectureUnit, student)).isEqualTo(completed);
    }

    @Test
    void testIsCompletedByUserInvalidLearningObject() {
        LearningObject unexpectedSubclass = new LearningObject() {

            @Override
            public boolean isCompletedFor(User user) {
                return false;
            }

            @Override
            public Optional<ZonedDateTime> getCompletionDate(User user) {
                return Optional.empty();
            }

            @Override
            public Long getId() {
                return 0L;
            }

            @Override
            public Set<Competency> getCompetencies() {
                return Set.of();
            }
        };
        assertThatThrownBy(() -> learningObjectService.isCompletedByUser(unexpectedSubclass, student)).isInstanceOf(IllegalArgumentException.class);
    }
}
