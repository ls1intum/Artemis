import { HttpClient, HttpContext } from '@angular/common/http';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MockProvider } from 'ng-mocks';
import { ScienceCourseConsent, ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { ScienceService } from 'app/foundation/science/science.service';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';

describe('ScienceService', () => {
    const consented = (courseId: number): ScienceCourseConsent => ({ courseId, active: true, scienceEnabled: true });

    let scienceService: ScienceService;
    let putStub: ReturnType<typeof vi.spyOn>;
    let featureActive: BehaviorSubject<boolean>;
    let getConsentForCourse: ReturnType<typeof vi.fn<(courseId: number) => Observable<ScienceCourseConsent | undefined>>>;
    let router: { url: string };

    beforeEach(() => {
        featureActive = new BehaviorSubject(true);
        getConsentForCourse = vi.fn((courseId: number) => of(courseId === 1 ? consented(1) : { courseId, scienceEnabled: true }));
        router = { url: '/courses/1/exercises' };
        TestBed.configureTestingModule({
            providers: [
                ScienceService,
                { provide: HttpClient, useClass: MockHttpService },
                MockProvider(FeatureToggleService, { getFeatureToggleActive: () => featureActive.asObservable() }),
                MockProvider(ScienceSettingsService, { getConsentForCourse }),
                { provide: Router, useValue: router },
            ],
        });

        putStub = vi.spyOn(TestBed.inject(HttpClient), 'put');
        scienceService = TestBed.inject(ScienceService);
    });

    it('should log a course-scoped event for the loaded course with active consent', () => {
        scienceService.loadConsent(1);

        scienceService.logEvent(ScienceEventType.LECTURE__OPEN);

        expect(getConsentForCourse).toHaveBeenCalledWith(1);
        expect(putStub).toHaveBeenCalledOnce();
        expect(putStub).toHaveBeenCalledWith(
            'api/atlas/science',
            expect.objectContaining({ type: ScienceEventType.LECTURE__OPEN, courseId: 1 }),
            expect.objectContaining({ observe: 'response' }),
        );
        // A failed log must never raise the global error alert over what the student is doing.
        const options = putStub.mock.calls[0][2] as { context: HttpContext };
        expect(options.context.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
    });

    it('should prefer the course the caller names over the one in the route', () => {
        router.url = '/courses/2/lectures';
        scienceService.loadConsent(1);

        scienceService.logEvent(ScienceEventType.LECTURE__OPEN, 5, 1);

        expect(putStub).toHaveBeenCalledWith('api/atlas/science', expect.objectContaining({ courseId: 1, resourceId: 5 }), expect.objectContaining({ observe: 'response' }));
    });

    it('should drop an event for a course other than the loaded one', () => {
        scienceService.loadConsent(1);

        scienceService.logEvent(ScienceEventType.LECTURE__OPEN, 5, 2);

        expect(putStub).not.toHaveBeenCalled();
    });

    it('should drop an event before any consent was loaded', () => {
        scienceService.logEvent(ScienceEventType.LECTURE__OPEN);

        expect(putStub).not.toHaveBeenCalled();
    });

    it('should not send an event outside a course route', () => {
        scienceService.loadConsent(1);
        router.url = '/course-management/1/exercises';

        scienceService.logEvent(ScienceEventType.LECTURE__OPEN);

        expect(putStub).not.toHaveBeenCalled();
    });

    it('should not log while the student has not decided, has opted out or the course stopped collecting', () => {
        for (const consent of [
            { courseId: 1, scienceEnabled: true },
            { courseId: 1, active: false, scienceEnabled: true },
            { courseId: 1, active: true, scienceEnabled: false },
        ]) {
            getConsentForCourse.mockReturnValueOnce(of(consent));
            scienceService.loadConsent(1);

            scienceService.logEvent(ScienceEventType.LECTURE__OPEN);
        }

        expect(putStub).not.toHaveBeenCalled();
    });

    it('should not log after switching to a course without consent', () => {
        scienceService.loadConsent(1);
        router.url = '/courses/2/exercises';

        scienceService.loadConsent(2);
        scienceService.logEvent(ScienceEventType.LECTURE__OPEN);

        expect(scienceService.courseConsent()).toEqual({ courseId: 2, scienceEnabled: true });
        expect(putStub).not.toHaveBeenCalled();
    });

    it('should clear the previous course and ignore its late response after a switch', () => {
        const courseOneResponse = new Subject<ScienceCourseConsent>();
        const courseTwoResponse = new Subject<ScienceCourseConsent>();
        getConsentForCourse
            .mockReturnValueOnce(of(consented(3)))
            .mockReturnValueOnce(courseOneResponse)
            .mockReturnValueOnce(courseTwoResponse);
        scienceService.loadConsent(3);

        scienceService.loadConsent(1);
        expect(scienceService.courseConsent()).toBeUndefined();
        scienceService.loadConsent(2);
        courseOneResponse.next(consented(1));

        expect(courseOneResponse.observed).toBe(false);
        expect(scienceService.courseConsent()).toBeUndefined();
        courseTwoResponse.next(consented(2));
        expect(scienceService.courseConsent()).toEqual(consented(2));
    });

    it('should leave the course without consent when the load fails', () => {
        getConsentForCourse.mockReturnValueOnce(throwError(() => new Error('boom')));

        scienceService.loadConsent(1);
        scienceService.logEvent(ScienceEventType.LECTURE__OPEN);

        expect(scienceService.courseConsent()).toBeUndefined();
        expect(putStub).not.toHaveBeenCalled();
    });

    it('should adopt a stored decision for the current course', () => {
        scienceService.loadConsent(2);

        scienceService.setConsent(consented(2));
        scienceService.logEvent(ScienceEventType.LECTURE__OPEN, undefined, 2);

        expect(scienceService.courseConsent()).toEqual(consented(2));
        expect(putStub).toHaveBeenCalledOnce();
    });

    it('should drop a stored decision that answers after a switch to another course', () => {
        scienceService.loadConsent(2);
        scienceService.loadConsent(1);

        scienceService.setConsent({ courseId: 2, active: false, scienceEnabled: true });

        expect(scienceService.courseConsent()).toEqual(consented(1));
    });

    it('should not load or log while the science feature is disabled', () => {
        featureActive.next(false);
        scienceService.loadConsent(1);

        scienceService.logEvent(ScienceEventType.LECTURE__OPEN);

        expect(getConsentForCourse).not.toHaveBeenCalled();
        expect(putStub).not.toHaveBeenCalled();
    });

    it('should load the consent once the feature is switched on mid-session', () => {
        featureActive.next(false);
        scienceService.loadConsent(1);

        featureActive.next(true);
        scienceService.logEvent(ScienceEventType.LECTURE__OPEN);

        expect(getConsentForCourse).toHaveBeenCalledOnce();
        expect(putStub).toHaveBeenCalledOnce();
    });

    it('should forget the consent and stop logging once the feature is switched off', () => {
        scienceService.loadConsent(1);

        featureActive.next(false);
        scienceService.logEvent(ScienceEventType.LECTURE__OPEN);

        expect(scienceService.courseConsent()).toBeUndefined();
        expect(putStub).not.toHaveBeenCalled();
    });
});
