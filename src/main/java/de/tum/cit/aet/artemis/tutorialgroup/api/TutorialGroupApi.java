package de.tum.cit.aet.artemis.tutorialgroup.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupService;

@Conditional(TutorialGroupEnabled.class)
@Controller
@Lazy
public class TutorialGroupApi extends AbstractTutorialGroupApi {

    private final TutorialGroupRepository tutorialGroupRepository;

    private final TutorialGroupService tutorialGroupService;

    public TutorialGroupApi(TutorialGroupRepository tutorialGroupRepository, TutorialGroupService tutorialGroupService) {
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupService = tutorialGroupService;
    }

    public Long countByCourseId(long courseId) {
        return tutorialGroupRepository.countByCourseId(courseId);
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
