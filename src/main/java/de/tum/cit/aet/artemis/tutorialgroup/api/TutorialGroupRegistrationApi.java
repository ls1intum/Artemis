package de.tum.cit.aet.artemis.tutorialgroup.api;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRegistrationRepository;

@Conditional(TutorialGroupEnabled.class)
@Controller
@Lazy
public class TutorialGroupRegistrationApi extends AbstractTutorialGroupApi {

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    public TutorialGroupRegistrationApi(TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository) {
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
    }

    public Set<TutorialGroupRegistration> findAllByTutorialGroupAndType(TutorialGroup tutorialGroup, TutorialGroupRegistrationType type) {
        return tutorialGroupRegistrationRepository.findAllByTutorialGroupAndType(tutorialGroup, type);
    }

    /**
     * Finds all tutorial group registrations for a user for GDPR data export.
     *
     * @param userId the ID of the user
     * @return list of all registrations for the user with tutorial group information
     */
    public List<TutorialGroupRegistration> findAllByStudentIdForExport(long userId) {
        return tutorialGroupRegistrationRepository.findAllByStudentIdWithTutorialGroupAndCourse(userId);
    }
}
