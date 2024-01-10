package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class LectureImportServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "lectureimport";

    @Autowired
    private LectureImportService lectureImportService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    private Lecture lecture1;

    private Course course2;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        List<Course> courses = lectureUtilService.createCoursesWithExercisesAndLecturesAndLectureUnits(TEST_PREFIX, false, true, 0);
        Course course1 = this.courseRepository.findByIdWithExercisesAndLecturesElseThrow(courses.get(0).getId());
        long lecture1Id = course1.getLectures().stream().findFirst().orElseThrow().getId();
        this.lecture1 = this.lectureRepository.findByIdWithAttachmentsAndPostsAndLectureUnitsAndCompetenciesAndCompletionsElseThrow(lecture1Id);
        this.course2 = courseUtilService.createCourse();

        assertThat(this.lecture1.getLectureUnits()).isNotEmpty();
        assertThat(this.lecture1.getAttachments()).isNotEmpty();
    }

    @AfterEach
    void tearDown() {
        // Delete lecture, which removes testing files on disk for associated attachments
        lectureRepository.delete(this.lecture1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportLectureToCourse() {
        int lectureCount = this.course2.getLectures().size();

        lectureImportService.importLecture(this.lecture1, this.course2);

        assertThat(this.course2.getLectures()).hasSize(lectureCount + 1);

        // Find the imported lecture and fetch it with lecture units
        Long lecture2Id = this.course2.getLectures().stream().skip(lectureCount).findFirst().orElseThrow().getId();
        Lecture lecture2 = this.lectureRepository.findByIdWithAttachmentsAndPostsAndLectureUnitsAndCompetenciesAndCompletionsElseThrow(lecture2Id);

        assertThat(lecture2.getTitle()).isEqualTo(this.lecture1.getTitle());
        assertThat(lecture2.getDescription()).isNotNull().isEqualTo(this.lecture1.getDescription());
        assertThat(lecture2.getStartDate()).isNotNull().isEqualTo(this.lecture1.getStartDate());
        assertThat(lecture2.getEndDate()).isNotNull().isEqualTo(this.lecture1.getEndDate());
        assertThat(lecture2.getVisibleDate()).isNotNull().isEqualTo(this.lecture1.getVisibleDate());

        // Assert that all lecture units (except exercise units) were copied
        assertThat(lecture2.getLectureUnits().stream().map(LectureUnit::getName).toList()).containsExactlyElementsOf(
                this.lecture1.getLectureUnits().stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).map(LectureUnit::getName).toList());

        assertThat(lecture2.getAttachments().stream().map(Attachment::getName).toList())
                .containsExactlyElementsOf(this.lecture1.getAttachments().stream().map(Attachment::getName).toList());

        lectureRepository.delete(lecture2);
    }
}
