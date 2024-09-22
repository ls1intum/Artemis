package de.tum.cit.aet.artemis.atlas.api;

import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.repository.ScienceEventRepository;

@Controller
public class ScienceEventApi extends AbstractAtlasApi {

    private final Optional<ScienceEventRepository> optionalScienceEventRepository;

    public ScienceEventApi(Environment environment, Optional<ScienceEventRepository> optionalScienceEventRepository) {
        super(environment);
        this.optionalScienceEventRepository = optionalScienceEventRepository;
    }

    public void renameIdentity(String oldIdentity, String newIdentity) {
        optionalScienceEventRepository.ifPresent(service -> renameIdentity(oldIdentity, newIdentity));
    }
}
