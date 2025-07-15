package de.tum.cit.aet.artemis.tutorialgroup.api;

import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRegistrationRepository;

@ConditionalOnProperty(name = "artemis.tutorialgroup.enabled", havingValue = "true")
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
