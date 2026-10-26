package de.tum.cit.aet.artemis.iris.api;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.CourseHomeSectionStatus;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseHomeSessionsDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/** Reads private history metadata without starting a chat or contacting a model. */
@Controller
@Lazy
@Conditional(IrisEnabled.class)
public class IrisCourseHomeApi extends AbstractIrisApi {

    private final IrisChatSessionRepository sessions;

    private final IrisSettingsService settings;

    private final UserAiPreferenceService preferences;

    private final AuthorizationCheckService authorization;

    public IrisCourseHomeApi(IrisChatSessionRepository sessions, IrisSettingsService settings, UserAiPreferenceService preferences, AuthorizationCheckService authorization) {
        this.sessions = sessions;
        this.settings = settings;
        this.preferences = preferences;
        this.authorization = authorization;
    }

    /**
     * Loads the authorized, bounded Home summary without resource graphs or read-state writes.
     *
     * @param courseId the authorized course identifier
     * @param user     the authenticated user
     * @param asOf     the single server timestamp for this snapshot
     * @param since    the preserved new-content baseline
     * @return bounded metadata for the student's existing course conversations
     */
    public IrisCourseHomeSessionsDTO load(long courseId, User user, Instant asOf, Instant since) {
        if (!authorization.isAtLeastStudentInCourse(courseId)) {
            throw new AccessForbiddenException("course", courseId);
        }
        if (!preferences.hasOptedIntoLlmUsage(user.getId()) || !settings.isEnabledForCourse(courseId)) {
            return new IrisCourseHomeSessionsDTO(CourseHomeSectionStatus.DISABLED, List.of());
        }
        var items = sessions.findForCourseHome(courseId, user.getId(), ZonedDateTime.ofInstant(asOf, ZoneOffset.UTC), PageRequest.of(0, 3)).stream()
                .map(row -> new IrisCourseHomeSessionsDTO.ItemDTO(row.getId(), row.getTitle(), row.getMode().name(), row.getEntityId(), row.getEntityName(),
                        row.getLastActivity().toInstant()))
                .toList();
        return new IrisCourseHomeSessionsDTO(CourseHomeSectionStatus.AVAILABLE, items);
    }
}
