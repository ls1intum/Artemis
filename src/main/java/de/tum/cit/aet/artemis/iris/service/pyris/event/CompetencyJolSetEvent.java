package de.tum.cit.aet.artemis.iris.service.pyris.event;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyJol;

public class CompetencyJolSetEvent extends PyrisEvent<CompetencyJol> {

    public CompetencyJolSetEvent(CompetencyJol eventObject) {
        super(eventObject);
    }
}
