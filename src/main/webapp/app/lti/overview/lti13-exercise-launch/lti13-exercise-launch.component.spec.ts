import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ActivatedRouteSnapshot, Router, convertToParamMap } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { Lti13ExerciseLaunchComponent } from 'app/lti/overview/lti13-exercise-launch/lti13-exercise-launch.component';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';

describe('Lti13ExerciseLaunchComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<Lti13ExerciseLaunchComponent>;
    let comp: Lti13ExerciseLaunchComponent;
    let route: ActivatedRoute;
    let http: HttpClient;
    let accountService: AccountService;
    let sessionStorageService: SessionStorageService;
    let mockRouter: Router;
    let navigateSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        mockRouter = {
            navigate: vi.fn(() => Promise.resolve(true)),
        } as unknown as Router;
        navigateSpy = vi.spyOn(mockRouter, 'navigate');

        route = {
            snapshot: { queryParamMap: convertToParamMap({ state: 'state', id_token: 'id_token' }) },
        } as ActivatedRoute;

        await TestBed.configureTestingModule({
            imports: [Lti13ExerciseLaunchComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: route },
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useValue: mockRouter },
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(Lti13ExerciseLaunchComponent);
        comp = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
        sessionStorageService = TestBed.inject(SessionStorageService);
        sessionStorageService.store<string>('state', 'state');

        http = TestBed.inject(HttpClient);
    });

    afterEach(() => {
        sessionStorageService.clear();
        vi.restoreAllMocks();
        navigateSpy.mockClear();
    });

    it('onInit fail without state', () => {
        const httpStub = vi.spyOn(http, 'post');

        route.snapshot = { queryParamMap: convertToParamMap({ id_token: 'id_token' }) } as ActivatedRouteSnapshot;

        comp.ngOnInit();

        expect(comp.isLaunching).toBe(false);
        expect(httpStub).not.toHaveBeenCalled();
    });

    it('onInit fail without token', () => {
        const httpStub = vi.spyOn(http, 'post');

        route.snapshot = { queryParamMap: convertToParamMap({ state: 'state' }) } as ActivatedRouteSnapshot;

        comp.ngOnInit();

        expect(comp.isLaunching).toBe(false);
        expect(httpStub).not.toHaveBeenCalled();
    });

    it('onInit no targetLinkUri', () => {
        const httpStub = vi.spyOn(http, 'post').mockReturnValue(of({ ltiIdToken: 'id-token', clientRegistrationId: 'client-id' }));

        expect(comp.isLaunching).toBe(true);

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/lti/public/lti13/auth-login', expect.anything(), expect.anything());

        expect(comp.isLaunching).toBe(false);
    });

    it('onInit success to call launch endpoint', () => {
        vi.spyOn(comp, 'replaceWindowLocationWrapper').mockImplementation(() => {});
        const targetLink = window.location.host + '/targetLink';
        const httpStub = vi.spyOn(http, 'post').mockReturnValue(of({ targetLinkUri: targetLink, ltiIdToken: 'id-token', clientRegistrationId: 'client-id' }));

        expect(comp.isLaunching).toBe(true);

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/lti/public/lti13/auth-login', expect.anything(), expect.anything());
    });

    it('onInit launch fails on error', () => {
        const httpStub = simulateLtiLaunchError(http, 400);

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/lti/public/lti13/auth-login', expect.anything(), expect.anything());

        expect(comp.isLaunching).toBe(false);
    });

    it('should redirect user to login when 401 error occurs', async () => {
        vi.spyOn(comp, 'authenticateUserThenRedirect');
        vi.spyOn(comp, 'redirectUserToLoginThenTargetLink');
        const httpStub = simulateLtiLaunchError(http, 401);
        const identitySpy = vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(undefined));
        const authStateSpy = vi.spyOn(accountService, 'getAuthenticationState').mockReturnValue(of(undefined));

        comp.ngOnInit();
        await vi.waitFor(() => {
            expect(httpStub).toHaveBeenCalledWith('api/lti/public/lti13/auth-login', expect.anything(), expect.anything());
        });
        await fixture.whenStable();

        expect(comp.authenticateUserThenRedirect).toHaveBeenCalled();
        expect(identitySpy).toHaveBeenCalled();
        expect(comp.redirectUserToLoginThenTargetLink).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalledWith(['/']);
        expect(authStateSpy).toHaveBeenCalled();
    });

    it('should redirect user to target link when user is already logged in', async () => {
        const replaceWindowSpy = vi.spyOn(comp, 'replaceWindowLocationWrapper').mockImplementation(() => {});
        vi.spyOn(comp, 'authenticateUserThenRedirect');
        vi.spyOn(comp, 'redirectUserToTargetLink');
        const loggedInUserUser: User = { id: 3, login: 'lti_user', firstName: 'TestUser', lastName: 'Moodle' } as User;
        const httpStub = simulateLtiLaunchError(http, 401);
        const identitySpy = vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(loggedInUserUser));

        comp.ngOnInit();
        await vi.waitFor(() => {
            expect(comp.authenticateUserThenRedirect).toHaveBeenCalled();
        });
        await fixture.whenStable();

        expect(identitySpy).toHaveBeenCalled();
        expect(httpStub).toHaveBeenCalled();
        expect(httpStub).toHaveBeenCalledWith('api/lti/public/lti13/auth-login', expect.anything(), expect.anything());
        expect(replaceWindowSpy).toHaveBeenCalled();
        expect(comp.redirectUserToTargetLink).toHaveBeenCalled();
    });

    it('should redirect user to target link after user logged in', async () => {
        vi.spyOn(comp, 'replaceWindowLocationWrapper').mockImplementation(() => {});
        vi.spyOn(comp, 'authenticateUserThenRedirect');
        vi.spyOn(comp, 'redirectUserToTargetLink');
        vi.spyOn(comp, 'redirectUserToLoginThenTargetLink');
        const loggedInUserUser: User = { id: 3, login: 'lti_user', firstName: 'TestUser', lastName: 'Moodle' } as User;
        const httpStub = simulateLtiLaunchError(http, 401);
        const identitySpy = vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(undefined));
        const authStateSpy = vi.spyOn(accountService, 'getAuthenticationState').mockReturnValue(of(loggedInUserUser));

        comp.ngOnInit();
        await vi.waitFor(() => {
            expect(comp.authenticateUserThenRedirect).toHaveBeenCalled();
        });
        await fixture.whenStable();

        expect(identitySpy).toHaveBeenCalled();
        expect(authStateSpy).toHaveBeenCalled();
        expect(httpStub).toHaveBeenCalled();
        expect(httpStub).toHaveBeenCalledWith('api/lti/public/lti13/auth-login', expect.anything(), expect.anything());
        expect(navigateSpy).toHaveBeenCalled();
        expect(comp.redirectUserToLoginThenTargetLink).toHaveBeenCalled();
    });

    function simulateLtiLaunchError(http: HttpClient, status: number, headers: Record<string, unknown> = {}, error = {}) {
        return vi.spyOn(http, 'post').mockReturnValue(
            throwError(() => ({
                status,
                headers: { get: () => 'lti_user', ...headers },
                error: { targetLinkUri: 'https://example.com/lti/course/1', ...error },
            })),
        );
    }

    it('should navigate directly if URL is "/lti/select-course"', () => {
        const setShownViaLtiSpy = vi.spyOn(comp['ltiService'], 'setShownViaLti');
        const applyThemeSpy = vi.spyOn(comp['themeService'], 'applyThemePreference');

        comp.replaceWindowLocationWrapper('/lti/select-course');

        expect(setShownViaLtiSpy).toHaveBeenCalledWith(true);
        expect(applyThemeSpy).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalledWith(['/lti/select-course'], {
            queryParams: {},
            replaceUrl: true,
        });
    });

    it('should parse URL, detect isMultiLaunch, and navigate with query params', () => {
        const setShownViaLtiSpy = vi.spyOn(comp['ltiService'], 'setShownViaLti');
        const setMultiLaunchSpy = vi.spyOn(comp['ltiService'], 'setMultiLaunch');
        const applyThemeSpy = vi.spyOn(comp['themeService'], 'applyThemePreference');

        const testUrl = `https://example.com/some/path?isMultiLaunch=true&foo=bar`;

        comp.replaceWindowLocationWrapper(testUrl);

        expect(setShownViaLtiSpy).toHaveBeenCalledWith(true);
        expect(setMultiLaunchSpy).toHaveBeenCalledWith(true);
        expect(applyThemeSpy).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalledWith(['/some/path'], {
            queryParams: { isMultiLaunch: 'true', foo: 'bar' },
            replaceUrl: true,
        });
    });

    it('should handle successful LTI launch', () => {
        vi.spyOn(comp, 'replaceWindowLocationWrapper').mockImplementation(() => {});
        const targetLink = 'https://example.com/course/1';
        vi.spyOn(http, 'post').mockReturnValue(
            of({
                targetLinkUri: targetLink,
                ltiIdToken: 'test-token',
                clientRegistrationId: 'client-123',
            }),
        );

        comp.ngOnInit();

        expect(comp.replaceWindowLocationWrapper).toHaveBeenCalledWith(targetLink);
    });

    it('should store LTI session data correctly', () => {
        const storeSpy = vi.spyOn(sessionStorageService, 'store');

        comp.storeLtiSessionData('test-lti-token', 'test-client-id');

        expect(storeSpy).toHaveBeenCalledWith('ltiIdToken', 'test-lti-token');
        expect(storeSpy).toHaveBeenCalledWith('clientRegistrationId', 'test-client-id');
    });

    it('should not store session data without ltiIdToken', () => {
        const storeSpy = vi.spyOn(sessionStorageService, 'store');

        comp.storeLtiSessionData('', 'test-client-id');

        expect(storeSpy).not.toHaveBeenCalled();
    });

    it('should not store session data without clientRegistrationId', () => {
        const storeSpy = vi.spyOn(sessionStorageService, 'store');

        comp.storeLtiSessionData('test-lti-token', '');

        expect(storeSpy).not.toHaveBeenCalled();
    });

    it('should handle LTI launch error gracefully', () => {
        const removeSpy = vi.spyOn(sessionStorageService, 'remove');

        comp.handleLtiLaunchError();

        expect(removeSpy).toHaveBeenCalledWith('state');
        expect(comp.isLaunching).toBe(false);
    });

    it('should send request with proper parameters', () => {
        const httpSpy = vi.spyOn(http, 'post').mockReturnValue(of({ targetLinkUri: 'test' }));
        vi.spyOn(comp, 'replaceWindowLocationWrapper').mockImplementation(() => {});

        comp.sendRequest();

        expect(httpSpy).toHaveBeenCalledWith(
            'api/lti/public/lti13/auth-login',
            'state=state&id_token=id_token',
            expect.objectContaining({
                headers: expect.anything(),
            }),
        );
    });
});
