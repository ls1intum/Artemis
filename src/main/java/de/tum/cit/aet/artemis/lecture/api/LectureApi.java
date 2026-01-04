package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureImportService;
import de.tum.cit.aet.artemis.lecture.service.LectureService;

/**
 * API for managing lectures.
 */
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class LectureApi extends AbstractLectureApi {

    private final LectureService lectureService;

    private final LectureImportService lectureImportService;

    private final LectureRepository lectureRepository;

    public LectureApi(LectureService lectureService, LectureImportService lectureImportService, LectureRepository lectureRepository) {
        this.lectureService = lectureService;
        this.lectureImportService = lectureImportService;
        this.lectureRepository = lectureRepository;
    }

    public Set<Lecture> filterVisibleLecturesWithActiveAttachments(Course course, Set<Lecture> lecturesWithAttachments, User user) {
        return lectureService.filterVisibleLecturesWithActiveAttachments(course, lecturesWithAttachments, user);
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
