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

    public LectureApi(LectureService lectureService, LectureImportService lectureImportService) {
        this.lectureService = lectureService;
        this.lectureImportService = lectureImportService;
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

    public Set<CalendarEventDTO> getCalendarEventDTOsFromLectures(long courseId, boolean userIsStudent, Language language) {
        return lectureService.getCalendarEventDTOsFromLectures(courseId, userIsStudent, language);
    }
}
