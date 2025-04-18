package de.tum.cit.aet.artemis.tutorialgroup.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;

@Conditional(TutorialGroupEnabled.class)
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
