package de.tum.cit.aet.artemis.tutorialgroup.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.domain.notification.TutorialGroupNotification;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupNotificationRepository;

@Conditional(TutorialGroupEnabled.class)
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
