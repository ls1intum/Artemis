package de.tum.cit.aet.artemis.tutorialgroup.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;

/**
 * Exposes aggregated tutorial group numbers to callers outside the tutorial group module, namely the public usage dashboard.
 * Only counts are exposed, never who leads or attends a group.
 */
@Conditional(TutorialGroupEnabled.class)
@Controller
@Lazy
public class TutorialGroupUsageStatisticsApi extends AbstractTutorialGroupApi {

    private final TutorialGroupRepository tutorialGroupRepository;

    public TutorialGroupUsageStatisticsApi(TutorialGroupRepository tutorialGroupRepository) {
        this.tutorialGroupRepository = tutorialGroupRepository;
    }

    /**
     * @param courseIds the courses
     * @return the number of tutorial groups of these courses
     */
    public long countTutorialGroups(Set<Long> courseIds) {
        return tutorialGroupRepository.countByCourseIdIn(courseIds);
    }
}
