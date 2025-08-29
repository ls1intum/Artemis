package de.tum.cit.aet.artemis.iris.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING_AND_IRIS;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.service.IrisLectureUnitAutoIngestionService;

@Profile(PROFILE_CORE_AND_SCHEDULING_AND_IRIS)
@Lazy
@Controller
public class IrisLectureUnitAutoIngestionApi extends AbstractIrisApi {

    private final IrisLectureUnitAutoIngestionService irisLectureUnitAutoIngestionService;

    public IrisLectureUnitAutoIngestionApi(IrisLectureUnitAutoIngestionService irisLectureUnitAutoIngestionService) {
        this.irisLectureUnitAutoIngestionService = irisLectureUnitAutoIngestionService;
    }

    public void scheduleLectureUnitAutoIngestion(Long lectureUnitId) {
        this.irisLectureUnitAutoIngestionService.scheduleLectureUnitAutoIngestion(lectureUnitId);
    }

    public void cancelLectureUnitAutoIngestion(Long lectureUnitId) {
        this.irisLectureUnitAutoIngestionService.cancelLectureUnitAutoIngestion(lectureUnitId);
    }
}
