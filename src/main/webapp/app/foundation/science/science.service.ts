import { Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subject, catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { ScienceEventDTO, ScienceEventType } from 'app/foundation/science/science.model';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ScienceCourseConsent, ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';

/**
 * Holds the science consent of the course on screen and logs interaction events against it.
 *
 * Provided only by the course overview, so it lives and dies with that page, sign-out included: the consent of one
 * user can never answer for the next. Components outside the course overview cannot inject it.
 */
@Injectable()
export class ScienceService {
    private readonly httpClient = inject(HttpClient);
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly scienceSettingsService = inject(ScienceSettingsService);
    private readonly router = inject(Router);

    private readonly resourceURL = 'api/atlas';

    /** The consent state of the course last requested, or `undefined` while it loads, failed or the feature is off. */
    readonly courseConsent = signal<ScienceCourseConsent | undefined>(undefined);

    private readonly courseId$ = new Subject<number>();
    private requestedCourseId?: number;
    private featureToggleActive = false;
    /** Whether the consent of the requested course is still being looked up. */
    private consentPending = false;
    /** Events of the requested course logged while its consent was still being looked up. */
    private deferredEvents: { type: ScienceEventType; resourceId?: number; courseId: number }[] = [];

    constructor() {
        const featureToggleActive$ = this.featureToggleService.getFeatureToggleActive(FeatureToggle.Science);
        featureToggleActive$.pipe(takeUntilDestroyed()).subscribe((active) => (this.featureToggleActive = active));

        // switchMap cancels the request for a course the student has already left, and the reset to undefined is part of
        // the same inner stream, so it can never overtake the response it precedes. A toggle flip over the websocket
        // re-runs it, so the consent is loaded when the feature is switched on mid-session.
        combineLatest([this.courseId$, featureToggleActive$])
            .pipe(
                switchMap(([courseId, active]) =>
                    active
                        ? this.scienceSettingsService.getConsentForCourse(courseId).pipe(
                              map((consent) => ({ consent, pending: false })),
                              // A failure leaves the course without consent, so events are dropped and the prompt stays
                              // closed, rather than interrupting the course with an error the student cannot act on.
                              catchError(() => of({ consent: undefined, pending: false })),
                              startWith({ consent: undefined, pending: true }),
                          )
                        : of({ consent: undefined, pending: false }),
                ),
                takeUntilDestroyed(),
            )
            .subscribe(({ consent, pending }) => {
                this.courseConsent.set(consent);
                this.consentPending = pending;
                // The page a student enters on logs its event before the lookup answers. Those events wait for it and are
                // then sent or dropped by the answer; a new lookup drops the events of the one it replaces.
                const deferredEvents = this.deferredEvents;
                this.deferredEvents = [];
                if (!pending) {
                    deferredEvents.forEach((event) => this.logEvent(event.type, event.resourceId, event.courseId));
                }
            });
    }

    /**
     * Loads the consent state of the course the student has entered or switched to.
     */
    loadConsent(courseId: number): void {
        this.requestedCourseId = courseId;
        this.courseId$.next(courseId);
    }

    /**
     * Adopts a decision the student has just stored. A save that answers after the student switched to another course
     * belongs to the course they left and is dropped.
     */
    setConsent(consent: ScienceCourseConsent): void {
        if (consent.courseId === this.requestedCourseId) {
            this.courseConsent.set(consent);
        }
    }

    /**
     * Records an interaction, if the course collects science data and the student has agreed to it. While the consent of
     * the course is still loading, the event waits for it.
     *
     * @param type       what happened
     * @param resourceId the lecture, unit, exercise, competency or learning path it happened on
     * @param courseId   the course it belongs to. Callers that hold it pass it, because consent is per course and an
     *                   event attributed to the wrong one is a consent violation rather than a reporting inaccuracy.
     *                   Omitting it falls back to reading the course out of the route.
     */
    logEvent(type: ScienceEventType, resourceId?: number, courseId?: number): void {
        const resolvedCourseId = courseId ?? this.inferCourseId();
        if (!this.featureToggleActive || resolvedCourseId === undefined) {
            return;
        }
        if (this.consentPending && resolvedCourseId === this.requestedCourseId) {
            this.deferredEvents.push({ type, resourceId, courseId: resolvedCourseId });
            return;
        }
        const consent = this.courseConsent();
        if (consent?.courseId !== resolvedCourseId || !consent.scienceEnabled || consent.active !== true) {
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
        const match = this.router.url.match(/\/courses\/(\d+)/);
        return match ? Number(match[1]) : undefined;
    }
}
