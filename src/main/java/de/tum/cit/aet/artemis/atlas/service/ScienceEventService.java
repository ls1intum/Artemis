package de.tum.cit.aet.artemis.atlas.service;

import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.atlas.dto.ScienceEventDTO;
import de.tum.cit.aet.artemis.atlas.repository.ScienceCourseConsentRepository;
import de.tum.cit.aet.artemis.atlas.repository.ScienceEnabledCourseRepository;
import de.tum.cit.aet.artemis.atlas.repository.ScienceEventRepository;

/**
 * Service class for {@link ScienceEvent}.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class ScienceEventService {

    private static final Logger log = LoggerFactory.getLogger(ScienceEventService.class);

    public static final Set<ScienceEventType> SCIENCE_AUDIT_EVENT_TYPES = EnumSet.of(ScienceEventType.SCIENCE__OPT_IN, ScienceEventType.SCIENCE__OPT_OUT,
            ScienceEventType.SCIENCE__DATA_DELETED);

    private final ScienceEventRepository scienceEventRepository;

    private final ScienceEnabledCourseRepository scienceEnabledCourseRepository;

    private final ScienceCourseConsentRepository scienceCourseConsentRepository;

    private final UserRepository userRepository;

    public ScienceEventService(ScienceEventRepository scienceEventRepository, ScienceEnabledCourseRepository scienceEnabledCourseRepository,
            ScienceCourseConsentRepository scienceCourseConsentRepository, UserRepository userRepository) {
        this.scienceEventRepository = scienceEventRepository;
        this.scienceEnabledCourseRepository = scienceEnabledCourseRepository;
        this.scienceCourseConsentRepository = scienceCourseConsentRepository;
        this.userRepository = userRepository;
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
        if (SCIENCE_AUDIT_EVENT_TYPES.contains(eventDTO.type())) {
            return;
        }
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logEvent(eventDTO, auth.getName(), true);
    }

    /**
     * Logs a science audit event without requiring existing active consent. This is used for consent changes and data deletion markers.
     *
     * @param principal the identity for whom the audit event is logged
     * @param type      the science audit event type
     * @param courseId  the course context
     */
    public void logAuditEvent(String principal, ScienceEventType type, long courseId) {
        if (!SCIENCE_AUDIT_EVENT_TYPES.contains(type)) {
            return;
        }
        logEvent(new ScienceEventDTO(type, courseId, courseId), principal, false);
    }

    /**
     * Logs the event for the given principal with the current timestamp.
     *
     * @param eventDTO  the DTO of the event that should be logged
     * @param principal the name of the principal for whom the event should be logged
     */
    private void logEvent(ScienceEventDTO eventDTO, String principal, boolean applyConsentGate) {
        logEvent(eventDTO, principal, ZonedDateTime.now(), applyConsentGate);
    }

    /**
     * Logs the event for the given principal with the given timestamp.
     *
     * @param eventDTO  the DTO of the event that should be logged
     * @param principal the name of the principal for whom the event should be logged
     * @param timestamp the time when the event happened
     */
    private void logEvent(ScienceEventDTO eventDTO, String principal, ZonedDateTime timestamp, boolean applyConsentGate) {
        if (applyConsentGate && !mayLogInteractionEvent(principal, eventDTO.courseId())) {
            return;
        }
        ScienceEvent event = new ScienceEvent();
        event.setIdentity(principal);
        event.setTimestamp(timestamp);
        event.setType(eventDTO.type());
        event.setResourceId(eventDTO.resourceId());
        event.setCourseId(eventDTO.courseId());
        scienceEventRepository.save(event);
    }

    /**
     * Checks whether the current science configuration allows interaction event logging for a principal in a course.
     *
     * @param principal the user login
     * @param courseId  the course id
     * @return true if science logging is enabled and the user has active consent
     */
    public boolean mayLogInteractionEvent(String principal, long courseId) {
        if (!scienceEnabledCourseRepository.existsByCourseIdAndActiveTrue(courseId)) {
            return false;
        }
        User user = userRepository.findOneByLogin(principal).orElse(null);
        return user != null && scienceCourseConsentRepository.existsByUserIdAndCourseIdAndActiveTrue(user.getId(), courseId);
    }

}
