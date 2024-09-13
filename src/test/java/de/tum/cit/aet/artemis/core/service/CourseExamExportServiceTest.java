package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.export.CourseExamExportService;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseExamExportServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exam_export";

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private CourseExamExportService courseExamExportService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setup() {
        // setup users
        userUtilService.addUsers(TEST_PREFIX, 2, 3, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourseExams() throws IOException {
        var course = courseUtilService.createCourseWithExamExercisesAndSubmissions(TEST_PREFIX);
        var exam = examRepository.findByCourseId(course.getId()).stream().findFirst().orElseThrow();
        List<String> exportErrors = new ArrayList<>();
        assertThatNoException().isThrownBy(() -> courseExamExportService.exportExam(exam, Path.of("tmp/export"), exportErrors));

        assertThat(exportErrors).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourse() throws IOException {
        // Add tutor for complaint response
        User tutor = userUtilService.createAndSaveUser(TEST_PREFIX + "tutor5");
        tutor.setGroups(Set.of("tutor"));
        userRepository.save(tutor);

        var course = courseUtilService.createCourseWithExamExercisesAndSubmissions(TEST_PREFIX);
        var courseWithExercises = courseUtilService.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 3, 2, 1, 1, true, 1, "");
        var exercises = courseWithExercises.getExercises();
        exercises.forEach(exercise -> {
            exercise.setCourse(course);
        });
        exerciseRepository.saveAll(exercises);
        course.setExercises(courseWithExercises.getExercises());
        courseRepository.save(course);

        List<String> exportErrors = new ArrayList<>();
        assertThatNoException().isThrownBy(() -> courseExamExportService.exportCourse(course, Path.of("tmp/export"), exportErrors));

        assertThat(exportErrors).isEmpty();
    }
}
