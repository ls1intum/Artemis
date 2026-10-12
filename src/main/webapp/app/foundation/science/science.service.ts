import { Service, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { ScienceEventDTO, ScienceEventType } from 'app/foundation/science/science.model';
import { AccountService } from 'app/core/auth/account.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { User } from 'app/account/user/user.model';
import { Router } from '@angular/router';
import { combineLatest } from 'rxjs';

@Service()
export class ScienceService {
    private httpClient = inject(HttpClient);
    private featureToggleService = inject(FeatureToggleService);
    private scienceSettingsService = inject(ScienceSettingsService);
    private accountService = inject(AccountService);
    private router = inject(Router);

    private resourceURL = 'api/atlas';

    private featureToggleActive = false;

    constructor() {
        combineLatest([this.accountService.getAuthenticationState(), this.featureToggleService.getFeatureToggleActive(FeatureToggle.Science)]).subscribe(([user, active]) => {
            this.featureToggleActive = active;
            this.refreshConsents(user, active);
        });
    }

    /**
     * Loads the consents once a user is signed in and the feature is on, which includes the feature being switched on
     * mid-session. Never while it is off: the endpoint is behind the feature toggle and would answer every sign-in with
     * a 403. Signing out forgets them, as they belong to the user who signed out.
     */
    private refreshConsents(user: User | undefined, featureActive: boolean): void {
        if (!user) {
            this.scienceSettingsService.clearScienceSettings();
            return;
        }
        if (featureActive) {
            // A failure here means events are dropped for want of a cached consent until the next identity change.
            // Not surfaced to the student, who cannot act on it, and not logged: console statements are banned by the
            // lint configuration and there is no client-side log sink.
            this.scienceSettingsService.refreshScienceSettings().subscribe({ error: () => undefined });
        }
    }

    private eventLoggingActive(courseId?: number) {
        return this.featureToggleActive && this.scienceSettingsService.eventLoggingAllowed(courseId);
    }

    /**
     * Records an interaction, if the course collects science data and the student has agreed to it.
     *
     * @param type       what happened
     * @param resourceId the lecture, unit, exercise, competency or learning path it happened on
     * @param courseId   the course it belongs to. Callers that hold it pass it, because consent is per course and an
     *                   event attributed to the wrong one is a consent violation rather than a reporting inaccuracy.
     *                   Omitting it falls back to reading the course out of the route.
     */
    logEvent(type: ScienceEventType, resourceId?: number, courseId?: number): void {
        const resolvedCourseId = courseId ?? this.inferCourseId();
        if (!this.eventLoggingActive(resolvedCourseId)) {
            return;
        }
        const event = new ScienceEventDTO();
        event.type = type;
        event.courseId = resolvedCourseId;
        if (resourceId) {
            event.resourceId = resourceId;
        }
        // Logging is a side effect of what the student is doing, so a failure is never theirs to see: neither a global
        // error alert nor an unhandled error.
        const context = new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true);
        this.httpClient.put<void>(`${this.resourceURL}/science`, event, { observe: 'response', context }).subscribe({ error: () => undefined });
    }

    /**
     * Reads the course from the current route, for the callers that do not hold it - the lecture unit components and
     * the learning path navigation, which are given only the resource they act on.
     *
     * An event logged outside a course route carries no course and is dropped rather than collected: consent is given
     * per course, so an event that cannot name one cannot be covered by a decision the student made.
     */
    private inferCourseId(): number | undefined {
        const match = this.router.url.match(/\/(?:courses|course-management)\/(\d+)/);
        if (!match?.[1]) {
            return undefined;
        }
        return Number(match[1]);
    }
}
