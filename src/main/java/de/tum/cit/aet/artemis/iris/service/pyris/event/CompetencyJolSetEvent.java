package de.tum.cit.aet.artemis.iris.service.pyris.event;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyJol;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;

public class CompetencyJolSetEvent extends PyrisEvent<IrisCourseChatSessionService, CompetencyJol> {

    private final CompetencyJol eventObject;

    public CompetencyJolSetEvent(CompetencyJol eventObject) {
        if (eventObject == null) {
            throw new IllegalArgumentException("Event object cannot be null");
        }
        this.eventObject = eventObject;
    }

    @Override
    public void handleEvent(IrisCourseChatSessionService service) {
        service.onJudgementOfLearningSet(eventObject);
    }
}
