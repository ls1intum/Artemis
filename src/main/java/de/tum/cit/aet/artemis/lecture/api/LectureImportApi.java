package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.service.LectureImportService;

/**
 * API for importing lecture data.
 */
@Profile(PROFILE_CORE)
@Controller
public class LectureImportApi extends AbstractLectureApi {

    private final LectureImportService lectureImportService;

    public LectureImportApi(LectureImportService lectureImportService) {
        this.lectureImportService = lectureImportService;
    }

    public Lecture importLecture(final Lecture importedLecture, final Course course, boolean importLectureUnits) {
        return lectureImportService.importLecture(importedLecture, course, importLectureUnits);
    }
}
