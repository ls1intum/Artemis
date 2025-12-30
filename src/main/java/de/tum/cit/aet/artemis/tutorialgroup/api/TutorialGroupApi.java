package de.tum.cit.aet.artemis.tutorialgroup.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRegistrationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupService;

@Conditional(TutorialGroupEnabled.class)
@Controller
@Lazy
public class TutorialGroupApi extends AbstractTutorialGroupApi {

    private final TutorialGroupRepository tutorialGroupRepository;

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    private final TutorialGroupService tutorialGroupService;

    public TutorialGroupApi(TutorialGroupRepository tutorialGroupRepository, TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository,
            TutorialGroupService tutorialGroupService) {
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
        this.tutorialGroupService = tutorialGroupService;
    }

    public Long countByCourseId(long courseId) {
        return tutorialGroupRepository.countByCourseId(courseId);
    }

    public Set<TutorialGroup> findAllByCourseId(Long courseId) {
        return tutorialGroupRepository.findAllByCourseId(courseId);
    }

    public void deleteById(Long id) {
        // We must first fetch the entity with its sessions to ensure JPA cascade works correctly.
        // Using deleteById directly may not trigger cascades if the entity's collections aren't loaded.
        tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(id).ifPresent(tutorialGroupRepository::delete);
    }

    public Set<CalendarEventDTO> getCalendarEventDTOsFromTutorialsGroups(long userId, Long courseId) {
        return tutorialGroupService.getCalendarEventDTOsFromTutorialsGroups(userId, courseId);
    }

    /**
     * Deletes all tutorial group registrations for a given course.
     * This removes student registrations while preserving tutorial group definitions.
     *
     * @param courseId the ID of the course
     */
    public void deleteAllRegistrationsByCourseId(long courseId) {
        tutorialGroupRegistrationRepository.deleteAllByTutorialGroupCourseId(courseId);
    }

    /**
     * Finds all tutorial group registrations for a given course.
     *
     * @param courseId the ID of the course
     * @return set of all registrations in the course
     */
    public Set<TutorialGroupRegistration> findAllRegistrationsByCourseId(long courseId) {
        return tutorialGroupRegistrationRepository.findAllByTutorialGroupCourseId(courseId);
    }

    /**
     * Counts the number of tutorial group registrations for a given course.
     *
     * @param courseId the ID of the course
     * @return the number of registrations
     */
    public long countRegistrationsByCourseId(long courseId) {
        return tutorialGroupRegistrationRepository.findAllByTutorialGroupCourseId(courseId).size();
    }
}
