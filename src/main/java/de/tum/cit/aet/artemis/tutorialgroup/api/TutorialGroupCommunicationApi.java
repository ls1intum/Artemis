package de.tum.cit.aet.artemis.tutorialgroup.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;

@Profile(PROFILE_CORE)
@Controller
public class TutorialGroupCommunicationApi extends AbstractTutorialGroupApi {

    private final TutorialGroupRepository tutorialGroupRepository;

    public TutorialGroupCommunicationApi(TutorialGroupRepository tutorialGroupRepository) {
        this.tutorialGroupRepository = tutorialGroupRepository;
    }

    public Pair<Long, String> getTutorialGroupCommunicationDetails(long channelId) {
        Optional<TutorialGroup> tutorialGroup = tutorialGroupRepository.findByTutorialGroupChannelId(channelId);
        return tutorialGroup.map(group -> Pair.of(group.getId(), group.getTitle())).orElse(null);

    }
}
