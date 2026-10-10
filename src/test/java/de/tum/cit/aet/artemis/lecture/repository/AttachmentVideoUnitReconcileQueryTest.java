package de.tum.cit.aet.artemis.lecture.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Verifies the queries the ingestion reconciler uses to decide which index rows to delete, against a real database.
 * The reconciler itself is covered by mock-based tests, which cannot show what these statements select.
 */
class AttachmentVideoUnitReconcileQueryTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Autowired
    private LectureTestRepository lectureRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private CourseTestRepository courseRepository;

    /**
     * A lecture marked as a tutorial lecture after its units were ingested leaves their rows in the index: the units
     * still exist, so the orphan check keeps them. The tutorial query must select exactly those units, with the
     * identity the deletion is scoped by.
     */
    @Test
    void testTutorialLectureUnitsAreSelectedForDeletionAndOrdinaryUnitsAreNot() {
        Lecture ordinaryLecture = lectureUtilService.createCourseWithLecture(true);
        AttachmentVideoUnit ordinaryUnit = lectureUtilService.createAttachmentVideoUnitWithoutAttachment(ordinaryLecture);
        Lecture unsavedTutorialLecture = lectureUtilService.createCourseWithLecture(false);
        unsavedTutorialLecture.setIsTutorialLecture(true);
        Lecture tutorialLecture = lectureRepository.save(unsavedTutorialLecture);
        AttachmentVideoUnit tutorialUnit = lectureUtilService.createAttachmentVideoUnitWithoutAttachment(tutorialLecture);
        long deletedUnitId = Long.MAX_VALUE;

        List<Long> censusUnitIds = List.of(ordinaryUnit.getId(), tutorialUnit.getId(), deletedUnitId);

        assertThat(attachmentVideoUnitRepository.findTutorialLectureUnitIdentities(censusUnitIds))
                .containsExactly(new IngestionJobIdentityDTO(tutorialLecture.getCourse().getId(), tutorialLecture.getId(), tutorialUnit.getId()));
        assertThat(attachmentVideoUnitRepository.findExistingIds(censusUnitIds)).as("the orphan check still only asks whether a unit exists")
                .containsExactlyInAnyOrder(ordinaryUnit.getId(), tutorialUnit.getId());
    }

    /**
     * The course walk is what garbage-collects a course's leftover index rows, so it must still reach a course whose last
     * unit was deleted while the Iris deletion failed. Test courses stay excluded.
     */
    @Test
    void testCourseWalkReachesCoursesWithoutUnitsAndSkipsTestCourses() {
        Lecture withUnit = lectureUtilService.createCourseWithLecture(true);
        lectureUtilService.createAttachmentVideoUnitWithoutAttachment(withUnit);
        Course withoutUnits = lectureUtilService.createCourseWithLecture(true).getCourse();
        Course testCourse = lectureUtilService.createCourseWithLecture(true).getCourse();
        testCourse.setTestCourse(true);
        courseRepository.save(testCourse);
        long cursor = withUnit.getCourse().getId() - 1;

        List<Long> walked = attachmentVideoUnitRepository.findReconcileCourseIdsAfter(cursor, PageRequest.of(0, 1000));

        assertThat(walked).contains(withUnit.getCourse().getId(), withoutUnits.getId()).doesNotContain(testCourse.getId()).isSorted();
        assertThat(attachmentVideoUnitRepository.findReconcileCourseIdsAfter(withoutUnits.getId(), PageRequest.of(0, 1000))).doesNotContain(withoutUnits.getId(),
                withUnit.getCourse().getId());
    }
}
