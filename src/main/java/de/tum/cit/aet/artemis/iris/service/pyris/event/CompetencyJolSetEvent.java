package de.tum.cit.aet.artemis.iris.service.pyris.event;

import java.util.Optional;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyJol;
import de.tum.cit.aet.artemis.core.domain.User;

public class CompetencyJolSetEvent extends PyrisEvent {

    private final CompetencyJol competencyJol;

    public CompetencyJolSetEvent(Object source, CompetencyJol competencyJol) {
        super(source);
        if (competencyJol == null) {
            throw new IllegalArgumentException("CompetencyJol cannot be null");
        }
        this.competencyJol = competencyJol;
    }

    public CompetencyJol getCompetencyJol() {
        return competencyJol;
    }

    @Override
    public Optional<User> getUser() {
        return Optional.ofNullable(competencyJol.getUser());
    }
}
