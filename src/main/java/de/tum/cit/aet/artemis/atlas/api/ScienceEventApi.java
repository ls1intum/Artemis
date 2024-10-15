package de.tum.cit.aet.artemis.atlas.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.repository.ScienceEventRepository;

@Profile(PROFILE_CORE)
@Controller
public class ScienceEventApi extends AbstractAtlasApi {

    private final Optional<ScienceEventRepository> scienceEventRepository;

    protected ScienceEventApi(Environment environment, Optional<ScienceEventRepository> scienceEventRepository) {
        super(environment);
        this.scienceEventRepository = scienceEventRepository;
    }

    public Set<ScienceEvent> findAllByIdentity(String login) {
        return getOrThrow(scienceEventRepository).findAllByIdentity(login);
    }

    public void renameIdentity(String oldIdentity, String newIdentity) {
        scienceEventRepository.ifPresent(repository -> repository.renameIdentity(oldIdentity, newIdentity));
    }
}
