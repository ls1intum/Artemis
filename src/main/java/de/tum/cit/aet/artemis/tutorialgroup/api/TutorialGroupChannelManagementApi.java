package de.tum.cit.aet.artemis.tutorialgroup.api;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupChannelManagementService;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupsConfigurationService;

@ConditionalOnProperty(name = "artemis.tutorialgroup.enabled", havingValue = "true")
@Controller
public class TutorialGroupChannelManagementApi extends AbstractTutorialGroupApi {

    private final TutorialGroupChannelManagementService tutorialGroupChannelManagementService;

    private final TutorialGroupsConfigurationService tutorialGroupsConfigurationService;

    public TutorialGroupChannelManagementApi(TutorialGroupChannelManagementService tutorialGroupChannelManagementService,
            TutorialGroupsConfigurationService tutorialGroupsConfigurationService) {
        this.tutorialGroupChannelManagementService = tutorialGroupChannelManagementService;
        this.tutorialGroupsConfigurationService = tutorialGroupsConfigurationService;
    }

    public Optional<TutorialGroup> getTutorialGroupBelongingToChannel(Channel channel) {
        return tutorialGroupChannelManagementService.getTutorialGroupBelongingToChannel(channel);
    }

    public void deleteTutorialGroupChannel(TutorialGroup tutorialGroup) {
        tutorialGroupChannelManagementService.deleteTutorialGroupChannel(tutorialGroup);
    }

    public void onTimeZoneUpdate(Course course) {
        tutorialGroupsConfigurationService.onTimeZoneUpdate(course);
    }
}
