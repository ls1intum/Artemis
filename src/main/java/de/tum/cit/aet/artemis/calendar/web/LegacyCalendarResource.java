package de.tum.cit.aet.artemis.calendar.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.util.CalendarSubscriptionFilterOption;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;

/**
 * Compatibility wrapper to keep existing calendar subscriptions working.
 * Moved from /api/core/calendar to /api/calendar.
 * Can be removed after October 2026 as most subscriptions should no longer be valid by then.
 * When removing this class, also remove the rest import exception in ArchitectureTest.java
 */
@Deprecated
@Lazy
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/core/calendar/")
public class LegacyCalendarResource {

    private final CalendarResource calendarResource;

    public LegacyCalendarResource(CalendarResource calendarResource) {
        this.calendarResource = calendarResource;
    }

    /**
     * GET api/core/calendar/course/:courseId/calendar-events-ics : gets all {@link CalendarEventDTO}s associated to the given course
     * that are visible to the user and returns them as an .ics file.
     *
     * @param courseId      the id of the course for which the events should be fetched
     * @param token         a shared secret between user and server that enables authentication/authorization
     * @param filterOptions a list of options that determines what DTOs are included in the .ics file
     * @param language      the language that is used to localize Vevent summaries
     * @return {@code 200 (OK)} with an .ics file containing Vevents representing the DTOs.
     * @throws EntityNotFoundException  {@code 404 (Not Found)} if no course exists for the provided courseId
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user associated to the token is not at least student in the course or if no user is associated to the token
     * @deprecated moved from /api/core/calendar to /api/calendar
     */
    @Deprecated
    @GetMapping(value = "courses/{courseId}/calendar-events-ics", produces = "text/calendar")
    public ResponseEntity<String> getLegacyCalendarEventSubscriptionFile(@PathVariable long courseId, @RequestParam("token") String token,
            @RequestParam("filterOptions") Set<CalendarSubscriptionFilterOption> filterOptions, @RequestParam("language") Language language) {
        // Forward to new resource for compatibility
        return calendarResource.getCalendarEventSubscriptionFile(courseId, token, filterOptions, language);
    }
}
