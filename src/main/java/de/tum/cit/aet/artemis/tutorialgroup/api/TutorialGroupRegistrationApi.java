package de.tum.cit.aet.artemis.tutorialgroup.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRegistrationRepository;

@Profile(PROFILE_CORE)
@Controller
public class TutorialGroupRegistrationApi extends AbstractTutorialGroupApi {

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    public TutorialGroupRegistrationApi(TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository) {
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
    }

    public Set<TutorialGroupRegistration> findAllByTutorialGroupAndType(TutorialGroup tutorialGroup, TutorialGroupRegistrationType type) {
        return tutorialGroupRegistrationRepository.findAllByTutorialGroupAndType(tutorialGroup, type);
    }

}
