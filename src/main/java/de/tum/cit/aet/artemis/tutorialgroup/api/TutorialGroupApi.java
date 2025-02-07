package de.tum.cit.aet.artemis.tutorialgroup.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupService;

@Profile(PROFILE_CORE)
@Controller
public class TutorialGroupApi extends AbstractTutorialGroupApi {

    private final TutorialGroupRepository tutorialGroupRepository;

    private final TutorialGroupService tutorialGroupService;

    public TutorialGroupApi(TutorialGroupRepository tutorialGroupRepository, TutorialGroupService tutorialGroupService) {
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupService = tutorialGroupService;
    }

    public Set<Long> findAllForNotifications(User user) {
        return tutorialGroupService.findAllForNotifications(user);
    }

    public Long countByCourse(Course course) {
        return tutorialGroupRepository.countByCourse(course);
    }

    public Set<TutorialGroup> findAllByCourseId(Long courseId) {
        return tutorialGroupRepository.findAllByCourseId(courseId);
    }

    public void deleteById(Long id) {
        tutorialGroupRepository.deleteById(id);
    }
}
