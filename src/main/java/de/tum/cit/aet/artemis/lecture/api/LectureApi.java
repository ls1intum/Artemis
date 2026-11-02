package de.tum.cit.aet.artemis.lecture.api;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.api.dtos.DemoLectures;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureDemoService;
import de.tum.cit.aet.artemis.lecture.service.LectureImportService;
import de.tum.cit.aet.artemis.lecture.service.LectureService;

/**
 * API for managing lectures.
 */
@Conditional(LectureEnabled.class)
@Controller
@Lazy
public class LectureApi extends AbstractLectureApi {

    private final LectureService lectureService;

    private final LectureImportService lectureImportService;

    private final LectureRepository lectureRepository;

    private final LectureDemoService lectureDemoService;

    public LectureApi(LectureService lectureService, LectureImportService lectureImportService, LectureRepository lectureRepository, LectureDemoService lectureDemoService) {
        this.lectureService = lectureService;
        this.lectureImportService = lectureImportService;
        this.lectureRepository = lectureRepository;
        this.lectureDemoService = lectureDemoService;
    }

    /**
     * Creates the lectures of the demo course with their units, as far as they do not exist yet, see {@link LectureDemoService#createDemo}.
     *
     * @param course                the demo course the lectures belong to.
     * @param architectureExercises the exercises about software architecture, linked from their lecture in this order.
     * @param algorithmsExercises   the exercises about algorithms and complexity, linked from their lecture in this order.
     * @param modelingExercises     the exercises about object-oriented modeling, linked from their lecture in this order.
     * @return the seeded units of each lecture.
     */
    public DemoLectures createDemo(Course course, List<Exercise> architectureExercises, List<Exercise> algorithmsExercises, List<Exercise> modelingExercises) {
        return lectureDemoService.createDemo(course, architectureExercises, algorithmsExercises, modelingExercises);
    }

    /**
     * Lets the demo student complete the seeded lecture units they worked through, see {@link LectureDemoService#completeDemoUnits}.
     *
     * @param lectures the seeded units of the demo lectures.
     * @param student  the demo student.
     */
    public void completeDemoUnits(DemoLectures lectures, User student) {
        lectureDemoService.completeDemoUnits(lectures, student);
    }

    public Lecture importLecture(final Lecture importedLecture, final Course course, boolean importLectureUnits) {
        return lectureImportService.importLecture(importedLecture, course, importLectureUnits);
    }

    public void delete(Lecture lecture, boolean updateCompetencyProgress) {
        lectureService.delete(lecture, updateCompetencyProgress);
    }

    /**
     * Deletes a lecture by its ID.
     *
     * @param lectureId                the ID of the lecture to delete
     * @param updateCompetencyProgress whether to update competency progress after deletion
     */
    public void deleteById(long lectureId, boolean updateCompetencyProgress) {
        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);
        lectureService.delete(lecture, updateCompetencyProgress);
    }

    /**
     * Finds all lecture IDs for a given course.
     *
     * @param courseId the ID of the course
     * @return set of lecture IDs
     */
    public Set<Long> findLectureIdsByCourseId(long courseId) {
        return lectureRepository.findLectureIdsByCourseId(courseId);
    }

    public Set<CalendarEventDTO> getCalendarEventDTOsFromLectures(long courseId, boolean userIsStudent, Language language) {
        return lectureService.getCalendarEventDTOsFromLectures(courseId, language);
    }
}
