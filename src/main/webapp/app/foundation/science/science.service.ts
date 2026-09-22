import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ScienceEventDTO, ScienceEventType } from 'app/foundation/science/science.model';
import { AccountService } from 'app/core/auth/account.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { User } from 'app/account/user/user.model';
import { Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class ScienceService {
    private httpClient = inject(HttpClient);
    private featureToggleService = inject(FeatureToggleService);
    private scienceSettingsService = inject(ScienceSettingsService);
    private accountService = inject(AccountService);
    private router = inject(Router);

    private resourceURL = 'api/atlas';

    private featureToggleActive = false;

    constructor() {
        this.accountService.getAuthenticationState().subscribe((user) => this.onUserIdentityChange(user));
        this.featureToggleService.getFeatureToggleActive(FeatureToggle.Science).subscribe((active) => {
            this.featureToggleActive = active;
        });
    }

    private onUserIdentityChange(user: User | undefined): void {
        if (user) {
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
        this.httpClient.put<void>(`${this.resourceURL}/science`, event, { observe: 'response' }).subscribe();
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
