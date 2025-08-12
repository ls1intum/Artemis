package de.tum.cit.aet.artemis.tutorialgroup.api;

import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupService;

@ConditionalOnProperty(name = "artemis.tutorialgroup.enabled", havingValue = "true")
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

    public Set<CalendarEventDTO> getCalendarEventDTOsFromTutorialsGroups(long userId, Long courseId) {
        return tutorialGroupService.getCalendarEventDTOsFromTutorialsGroups(userId, courseId);
    }
}
