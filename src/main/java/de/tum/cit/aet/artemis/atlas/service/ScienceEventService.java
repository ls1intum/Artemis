package de.tum.cit.aet.artemis.atlas.service;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.atlas.dto.ScienceEventDTO;
import de.tum.cit.aet.artemis.atlas.repository.ScienceCourseConsentRepository;
import de.tum.cit.aet.artemis.atlas.repository.ScienceEventRepository;

/**
 * Service class for {@link ScienceEvent}.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class ScienceEventService {

    private static final Logger log = LoggerFactory.getLogger(ScienceEventService.class);

    private final ScienceEventRepository scienceEventRepository;

    private final ScienceCourseConsentRepository scienceCourseConsentRepository;

    public ScienceEventService(ScienceEventRepository scienceEventRepository, ScienceCourseConsentRepository scienceCourseConsentRepository) {
        this.scienceEventRepository = scienceEventRepository;
        this.scienceCourseConsentRepository = scienceCourseConsentRepository;
    }

    /**
     * Logs the event for the current principal with the current timestamp.
     *
     * @param eventDTO the DTO of the event that should be logged
     */
    public void logEvent(ScienceEventDTO eventDTO) {
        if (eventDTO == null || eventDTO.type() == null || eventDTO.courseId() == null) {
            if (eventDTO != null && eventDTO.type() != null && eventDTO.courseId() == null) {
                log.debug("Dropped science event {} because no course id was provided", eventDTO.type());
            }
            return;
        }
        if (ScienceEventType.AUDIT_EVENT_TYPES.contains(eventDTO.type())) {
            return;
        }
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!mayLogInteractionEvent(auth.getName(), eventDTO.courseId())) {
            return;
        }
        scienceEventRepository.save(ScienceEvent.of(auth.getName(), eventDTO.type(), eventDTO.resourceId(), eventDTO.courseId(), ZonedDateTime.now()));
    }

    /**
     * Checks whether the current science configuration allows interaction event logging for a principal in a course.
     *
     * @param principal the user login
     * @param courseId  the course id
     * @return true if science logging is enabled and the user has active consent
     */
    private boolean mayLogInteractionEvent(String principal, long courseId) {
        // One query rather than three. This runs on every logged interaction, so the enablement check, the user lookup
        // and the consent check are answered together by the consent row's own join.
        return scienceCourseConsentRepository.existsActiveConsentForEnabledCourse(principal, courseId);
    }

}
