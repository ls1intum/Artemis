package de.tum.cit.aet.artemis.exam.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.service.ExamLiveEventsService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@ConditionalOnProperty(name = "artemis.exam.enabled", havingValue = "true")
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
