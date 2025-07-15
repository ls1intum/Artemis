package de.tum.cit.aet.artemis.atlas.api;

import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.repository.ScienceEventRepository;

@Controller
@ConditionalOnProperty(name = "artemis.atlas.enabled", havingValue = "true")
public class ScienceEventApi extends AbstractAtlasApi {

    private final ScienceEventRepository scienceEventRepository;

    public ScienceEventApi(ScienceEventRepository scienceEventRepository) {
        this.scienceEventRepository = scienceEventRepository;
    }

    public Set<ScienceEvent> findAllByIdentity(String login) {
        return scienceEventRepository.findAllByIdentity(login);
    }

    public void renameIdentity(String oldIdentity, String newIdentity) {
        scienceEventRepository.renameIdentity(oldIdentity, newIdentity);
    }
}
