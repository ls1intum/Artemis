import { HttpClient, HttpContext } from '@angular/common/http';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { ScienceService } from 'app/foundation/science/science.service';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';

describe('ScienceService', () => {
    let scienceService: ScienceService;
    let httpService: HttpClient;
    let putStub: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                ScienceService,
                { provide: HttpClient, useClass: MockHttpService },
                MockProvider(AccountService, { getAuthenticationState: () => of({} as User) }),
                MockProvider(FeatureToggleService, { getFeatureToggleActive: (feature: FeatureToggle) => of(feature === FeatureToggle.Science) }),
                MockProvider(ScienceSettingsService, {
                    getScienceSettingsUpdates: () => of([]),
                    refreshScienceSettings: () => of([]),
                    eventLoggingAllowed: (courseId?: number) => courseId === 1,
                }),
                MockProvider(Router, { url: '/courses/1/exercises' }),
            ],
        });

        httpService = TestBed.inject(HttpClient);
        scienceService = TestBed.inject(ScienceService);
        putStub = vi.spyOn(httpService, 'put');
    });

    it('should send a course-scoped request to the server to log event', () => {
        const type = ScienceEventType.LECTURE__OPEN;

        scienceService.logEvent(type);

        expect(putStub).toHaveBeenCalledOnce();
        expect(putStub).toHaveBeenCalledWith('api/atlas/science', expect.objectContaining({ type, courseId: 1 }), expect.objectContaining({ observe: 'response' }));
        // A failed log must never raise the global error alert over what the student is doing.
        const options = putStub.mock.calls[0][2] as { context: HttpContext };
        expect(options.context.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
    });

    it('should prefer the course the caller names over the one in the route', () => {
        // Consent is per course, so an event attributed to the course that happens to be in the URL rather than the one
        // it belongs to is a consent violation, not a reporting inaccuracy.
        scienceService.logEvent(ScienceEventType.LECTURE__OPEN, 5, 1);

        expect(putStub).toHaveBeenCalledWith('api/atlas/science', expect.objectContaining({ courseId: 1, resourceId: 5 }), expect.objectContaining({ observe: 'response' }));
    });

    it('should drop an event for a named course the student has not consented to', () => {
        scienceService.logEvent(ScienceEventType.LECTURE__OPEN, 5, 2);

        expect(putStub).not.toHaveBeenCalled();
    });

    it('should not send an event outside a course route', () => {
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            providers: [
                ScienceService,
                { provide: HttpClient, useClass: MockHttpService },
                MockProvider(AccountService, { getAuthenticationState: () => of({} as User) }),
                MockProvider(FeatureToggleService, { getFeatureToggleActive: (feature: FeatureToggle) => of(feature === FeatureToggle.Science) }),
                MockProvider(ScienceSettingsService, { refreshScienceSettings: () => of([]), eventLoggingAllowed: (courseId?: number) => courseId === 1 }),
                MockProvider(Router, { url: '/admin/science' }),
            ],
        });
        const put = vi.spyOn(TestBed.inject(HttpClient), 'put');

        TestBed.inject(ScienceService).logEvent(ScienceEventType.LECTURE__OPEN);

        expect(put).not.toHaveBeenCalled();
    });

    it('should not send an event when the science feature is disabled', () => {
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            providers: [
                ScienceService,
                { provide: HttpClient, useClass: MockHttpService },
                MockProvider(AccountService, { getAuthenticationState: () => of({} as User) }),
                MockProvider(FeatureToggleService, { getFeatureToggleActive: () => of(false) }),
                MockProvider(ScienceSettingsService, { refreshScienceSettings: () => of([]), eventLoggingAllowed: () => true }),
                MockProvider(Router, { url: '/courses/1/exercises' }),
            ],
        });
        const put = vi.spyOn(TestBed.inject(HttpClient), 'put');

        TestBed.inject(ScienceService).logEvent(ScienceEventType.LECTURE__OPEN);

        expect(put).not.toHaveBeenCalled();
    });

    it('should not send an event when course consent is not active', () => {
        vi.spyOn(TestBed.inject(ScienceSettingsService), 'eventLoggingAllowed').mockReturnValue(false);

        scienceService.logEvent(ScienceEventType.LECTURE__OPEN);

        expect(putStub).not.toHaveBeenCalled();
    });

    describe('consent refresh', () => {
        // The consents endpoint is behind the feature toggle, so loading them while it is off answers every sign-in
        // with a 403 - and the error alert it raised covered the top of every course page for its full timeout.
        const clearScienceSettings = vi.fn();
        const setUp = (user: User | undefined, scienceActive: BehaviorSubject<boolean>) => {
            const refreshScienceSettings = vi.fn(() => of([]));
            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                providers: [
                    ScienceService,
                    { provide: HttpClient, useClass: MockHttpService },
                    MockProvider(AccountService, { getAuthenticationState: () => of(user) }),
                    MockProvider(FeatureToggleService, { getFeatureToggleActive: () => scienceActive.asObservable() }),
                    MockProvider(ScienceSettingsService, { refreshScienceSettings, clearScienceSettings, eventLoggingAllowed: () => true }),
                    MockProvider(Router, { url: '/courses/1/exercises' }),
                ],
            });
            TestBed.inject(ScienceService);
            return refreshScienceSettings;
        };

        it('should not load consents while the science feature is disabled', () => {
            const refreshScienceSettings = setUp({} as User, new BehaviorSubject(false));

            expect(refreshScienceSettings).not.toHaveBeenCalled();
        });

        it('should not load consents while nobody is signed in', () => {
            const refreshScienceSettings = setUp(undefined, new BehaviorSubject(true));

            expect(refreshScienceSettings).not.toHaveBeenCalled();
        });

        it('should forget the cached consents once the user signs out', () => {
            // Otherwise the decisions of the previous user would answer for the next one until the next refresh.
            clearScienceSettings.mockClear();
            setUp(undefined, new BehaviorSubject(true));

            expect(clearScienceSettings).toHaveBeenCalledOnce();
        });

        it('should load consents once the feature is switched on while signed in', () => {
            const scienceActive = new BehaviorSubject(false);
            const refreshScienceSettings = setUp({} as User, scienceActive);

            scienceActive.next(true);

            expect(refreshScienceSettings).toHaveBeenCalledOnce();
        });
    });
});
