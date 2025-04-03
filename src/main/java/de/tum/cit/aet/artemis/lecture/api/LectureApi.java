package de.tum.cit.aet.artemis.lecture.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.service.LectureImportService;
import de.tum.cit.aet.artemis.lecture.service.LectureService;

/**
 * API for managing lectures.
 */
@Conditional(LectureEnabled.class)
@Controller
public class LectureApi extends AbstractLectureApi {

    private final LectureService lectureService;

    private final LectureImportService lectureImportService;

    public LectureApi(LectureService lectureService, LectureImportService lectureImportService) {
        this.lectureService = lectureService;
        this.lectureImportService = lectureImportService;
    }

    public void filterVisibleLecturesWithActiveAttachments(Course course, Set<Lecture> lecturesWithAttachments, User user) {
        lectureService.filterVisibleLecturesWithActiveAttachments(course, lecturesWithAttachments, user);
    }

    public Lecture importLecture(final Lecture importedLecture, final Course course, boolean importLectureUnits) {
        return lectureImportService.importLecture(importedLecture, course, importLectureUnits);
    }

    public void delete(Lecture lecture, boolean updateCompetencyProgress) {
        lectureService.delete(lecture, updateCompetencyProgress);
    }
}
