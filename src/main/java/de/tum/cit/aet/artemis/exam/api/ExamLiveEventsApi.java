package de.tum.cit.aet.artemis.exam.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.service.ExamLiveEventsService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Profile(PROFILE_CORE)
@Controller
public class ExamLiveEventsApi extends AbstractExamApi {

    private final ExamLiveEventsService examLiveEventsService;

    public ExamLiveEventsApi(ExamLiveEventsService examLiveEventsService) {
        this.examLiveEventsService = examLiveEventsService;
    }

    public void createAndSendProblemStatementUpdateEvent(Exercise exercise, String message) {
        examLiveEventsService.createAndSendProblemStatementUpdateEvent(exercise, message);
    }
}
