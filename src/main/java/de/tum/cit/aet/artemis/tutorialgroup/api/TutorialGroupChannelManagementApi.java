package de.tum.cit.aet.artemis.tutorialgroup.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupChannelManagementService;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupsConfigurationService;

@Conditional(TutorialGroupEnabled.class)
@Controller
@Lazy
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

    public void deleteTutorialGroupChannel(Long tutorialGroupId) {
        tutorialGroupChannelManagementService.deleteTutorialGroupChannel(tutorialGroupId);
    }

    public void onTimeZoneUpdate(Course course) {
        tutorialGroupsConfigurationService.onTimeZoneUpdate(course);
    }
}
