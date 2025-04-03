package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitImportService;

@Profile(PROFILE_CORE)
@Controller
public class LectureUnitImportApi extends AbstractLectureApi {

    private final LectureUnitImportService lectureUnitImportService;

    public LectureUnitImportApi(de.tum.cit.aet.artemis.lecture.service.LectureUnitImportService lectureUnitImportService) {
        this.lectureUnitImportService = lectureUnitImportService;
    }

    public LectureUnit importLectureUnit(LectureUnit sourceLectureUnit) {
        return lectureUnitImportService.importLectureUnit(sourceLectureUnit);
    }
}
