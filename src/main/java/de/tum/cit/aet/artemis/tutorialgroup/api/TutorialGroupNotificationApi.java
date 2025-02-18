package de.tum.cit.aet.artemis.tutorialgroup.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.domain.notification.TutorialGroupNotification;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupNotificationRepository;

@Profile(PROFILE_CORE)
@Controller
public class TutorialGroupNotificationApi extends AbstractTutorialGroupApi {

    private final TutorialGroupNotificationRepository tutorialGroupNotificationRepository;

    public TutorialGroupNotificationApi(TutorialGroupNotificationRepository tutorialGroupNotificationRepository) {
        this.tutorialGroupNotificationRepository = tutorialGroupNotificationRepository;
    }

    public void deleteAllByTutorialGroupId(Long tutorialGroupId) {
        tutorialGroupNotificationRepository.deleteAllByTutorialGroupId(tutorialGroupId);
    }

    public void save(TutorialGroupNotification tutorialGroupNotification) {
        tutorialGroupNotificationRepository.save(tutorialGroupNotification);
    }
}
