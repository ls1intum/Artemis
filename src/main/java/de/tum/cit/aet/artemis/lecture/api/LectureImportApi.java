package de.tum.cit.aet.artemis.lecture.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.service.LectureImportService;

/**
 * API for managing lectures.
 */
@Conditional(LectureEnabled.class)
@Controller
@Lazy
public class LectureImportApi extends AbstractLectureApi {

    private final LectureImportService lectureImportService;

    public LectureImportApi(LectureImportService lectureImportService) {
        this.lectureImportService = lectureImportService;
    }

    public Lecture importLecture(final Lecture importedLecture, final Course course, boolean importLectureUnits) {
        return lectureImportService.importLecture(importedLecture, course, importLectureUnits);
    }

}
