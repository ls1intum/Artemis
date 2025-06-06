package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LectureImportServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "lectureimport";

    @Autowired
    private LectureImportService lectureImportService;

    @Autowired
    private LectureTestRepository lectureRepository;

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
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLecturesAndLectureUnits(TEST_PREFIX, false, true, 0);
        Course course1 = courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        long lecture1Id = course1.getLectures().stream().findFirst().orElseThrow().getId();
        lecture1 = lectureRepository.findByIdWithAttachmentsAndLectureUnitsAndCompletionsElseThrow(lecture1Id);
        course2 = courseUtilService.createCourse();

        assertThat(lecture1.getLectureUnits()).isNotEmpty();
        assertThat(lecture1.getAttachments()).isNotEmpty();
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

        lectureImportService.importLecture(this.lecture1, this.course2, true);

        this.course2 = courseRepository.findByIdWithLecturesElseThrow(course2.getId());

        assertThat(this.course2.getLectures()).hasSize(lectureCount + 1);

        // Find the imported lecture and fetch it with lecture units
        Long lecture2Id = this.course2.getLectures().stream().skip(lectureCount).findFirst().orElseThrow().getId();
        Lecture lecture2 = this.lectureRepository.findByIdWithAttachmentsAndLectureUnitsAndCompletionsElseThrow(lecture2Id);

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
