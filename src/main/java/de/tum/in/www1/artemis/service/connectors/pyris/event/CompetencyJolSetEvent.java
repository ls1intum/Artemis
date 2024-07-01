package de.tum.in.www1.artemis.service.connectors.pyris.event;

import de.tum.in.www1.artemis.domain.competency.CompetencyJol;
import de.tum.in.www1.artemis.service.iris.session.IrisCourseChatSessionService;

public class CompetencyJolSetEvent extends PyrisEvent<IrisCourseChatSessionService, CompetencyJol> {

    private final CompetencyJol event;

    public CompetencyJolSetEvent(CompetencyJol event) {
        this.event = event;
    }

    @Override
    public void handleEvent(IrisCourseChatSessionService service) {
        service.onJudgementOfLearningSet(event);
    }
}
