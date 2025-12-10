import { HttpClient, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
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
    let fixture: ComponentFixture<Lti13ExerciseLaunchComponent>;
    let comp: Lti13ExerciseLaunchComponent;
    let route: ActivatedRoute;
    let http: HttpClient;
    let accountService: AccountService;
    let sessionStorageService: SessionStorageService;
    const mockRouter = {
        navigate: jest.fn(() => Promise.resolve(true)),
    } as unknown as Router;
    const navigateSpy = jest.spyOn(mockRouter, 'navigate');

    beforeEach(() => {
        route = {
            snapshot: { queryParamMap: convertToParamMap({ state: 'state', id_token: 'id_token' }) },
        } as ActivatedRoute;

        TestBed.configureTestingModule({
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
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(Lti13ExerciseLaunchComponent);
                comp = fixture.componentInstance;
                accountService = TestBed.inject(AccountService);
                sessionStorageService = TestBed.inject(SessionStorageService);
                sessionStorageService.store<string>('state', 'state');
            });

        http = TestBed.inject(HttpClient);
    });

    afterEach(() => {
        sessionStorageService.clear();
        jest.restoreAllMocks();
        navigateSpy.mockClear();
    });

    it('onInit fail without state', () => {
        const httpStub = jest.spyOn(http, 'post');

        route.snapshot = { queryParamMap: convertToParamMap({ id_token: 'id_token' }) } as ActivatedRouteSnapshot;

        comp.ngOnInit();

        expect(comp.isLaunching).toBeFalse();
        expect(httpStub).not.toHaveBeenCalled();
    });

    it('onInit fail without token', () => {
        const httpStub = jest.spyOn(http, 'post');

        route.snapshot = { queryParamMap: convertToParamMap({ state: 'state' }) } as ActivatedRouteSnapshot;

        comp.ngOnInit();

        expect(comp.isLaunching).toBeFalse();
        expect(httpStub).not.toHaveBeenCalled();
    });

    it('onInit no targetLinkUri', () => {
        const httpStub = jest.spyOn(http, 'post').mockReturnValue(of({ ltiIdToken: 'id-token', clientRegistrationId: 'client-id' }));

        expect(comp.isLaunching).toBeTrue();

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/lti/public/lti13/auth-login', expect.anything(), expect.anything());

        expect(comp.isLaunching).toBeFalse();
    });

    it('onInit success to call launch endpoint', () => {
        jest.spyOn(comp, 'replaceWindowLocationWrapper').mockImplementation();
        const targetLink = window.location.host + '/targetLink';
        const httpStub = jest.spyOn(http, 'post').mockReturnValue(of({ targetLinkUri: targetLink, ltiIdToken: 'id-token', clientRegistrationId: 'client-id' }));

        expect(comp.isLaunching).toBeTrue();

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/lti/public/lti13/auth-login', expect.anything(), expect.anything());
    });

    it('onInit launch fails on error', () => {
        const httpStub = simulateLtiLaunchError(http, 400);

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/lti/public/lti13/auth-login', expect.anything(), expect.anything());

        expect(comp.isLaunching).toBeFalse();
    });

    it('should redirect user to login when 401 error occurs', fakeAsync(() => {
        jest.spyOn(comp, 'authenticateUserThenRedirect');
        jest.spyOn(comp, 'redirectUserToLoginThenTargetLink');
        const httpStub = simulateLtiLaunchError(http, 401);
        const identitySpy = jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(undefined));
        const authStateSpy = jest.spyOn(accountService, 'getAuthenticationState').mockReturnValue(of(undefined));

        comp.ngOnInit();
        tick(1000);

        expect(httpStub).toHaveBeenCalledWith('api/lti/public/lti13/auth-login', expect.anything(), expect.anything());
        expect(comp.authenticateUserThenRedirect).toHaveBeenCalled();
        expect(identitySpy).toHaveBeenCalled();
        expect(comp.redirectUserToLoginThenTargetLink).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalledWith(['/']);
        expect(authStateSpy).toHaveBeenCalled();
    }));

    it('should redirect user to target link when user is already logged in', fakeAsync(() => {
        jest.spyOn(comp, 'replaceWindowLocationWrapper').mockImplementation();
        jest.spyOn(comp, 'authenticateUserThenRedirect');
        jest.spyOn(comp, 'redirectUserToTargetLink');
        const loggedInUserUser: User = { id: 3, login: 'lti_user', firstName: 'TestUser', lastName: 'Moodle' } as User;
        const httpStub = simulateLtiLaunchError(http, 401);
        const identitySpy = jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(loggedInUserUser));

        comp.ngOnInit();
        tick(1000);

        expect(comp.authenticateUserThenRedirect).toHaveBeenCalled();
        expect(identitySpy).toHaveBeenCalled();
        expect(httpStub).toHaveBeenCalled();
        expect(httpStub).toHaveBeenCalledWith('api/lti/public/lti13/auth-login', expect.anything(), expect.anything());
        expect(navigateSpy).not.toHaveBeenCalled();
        expect(comp.redirectUserToTargetLink).toHaveBeenCalled();
    }));

    it('should redirect user to target link after user logged in', fakeAsync(() => {
        jest.spyOn(comp, 'replaceWindowLocationWrapper').mockImplementation();
        jest.spyOn(comp, 'authenticateUserThenRedirect');
        jest.spyOn(comp, 'redirectUserToTargetLink');
        jest.spyOn(comp, 'redirectUserToLoginThenTargetLink');
        const loggedInUserUser: User = { id: 3, login: 'lti_user', firstName: 'TestUser', lastName: 'Moodle' } as User;
        const httpStub = simulateLtiLaunchError(http, 401);
        const identitySpy = jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(undefined));
        const authStateSpy = jest.spyOn(accountService, 'getAuthenticationState').mockReturnValue(of(loggedInUserUser));

        comp.ngOnInit();
        tick(1000);

        expect(comp.authenticateUserThenRedirect).toHaveBeenCalled();
        expect(identitySpy).toHaveBeenCalled();
        expect(authStateSpy).toHaveBeenCalled();
        expect(httpStub).toHaveBeenCalled();
        expect(httpStub).toHaveBeenCalledWith('api/lti/public/lti13/auth-login', expect.anything(), expect.anything());
        expect(navigateSpy).toHaveBeenCalled();
        expect(comp.redirectUserToLoginThenTargetLink).toHaveBeenCalled();
    }));

    function simulateLtiLaunchError(http: HttpClient, status: number, headers: any = {}, error = {}) {
        return jest.spyOn(http, 'post').mockReturnValue(
            throwError(() => ({
                status,
                headers: Object.assign({ get: () => 'lti_user' }, headers),
                error: Object.assign({ targetLinkUri: 'mockTargetLinkUri' }, error),
            })),
        );
    }

    it('should navigate directly if URL is "/lti/select-course"', () => {
        const setShownViaLtiSpy = jest.spyOn(comp['ltiService'], 'setShownViaLti');
        const applyThemeSpy = jest.spyOn(comp['themeService'], 'applyThemePreference');

        comp.replaceWindowLocationWrapper('/lti/select-course');

        expect(setShownViaLtiSpy).toHaveBeenCalledWith(true);
        expect(applyThemeSpy).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalledWith(['/lti/select-course'], {
            queryParams: {},
            replaceUrl: true,
        });
    });

    it('should parse URL, detect isMultiLaunch, and navigate with query params', () => {
        const setShownViaLtiSpy = jest.spyOn(comp['ltiService'], 'setShownViaLti');
        const setMultiLaunchSpy = jest.spyOn(comp['ltiService'], 'setMultiLaunch');
        const applyThemeSpy = jest.spyOn(comp['themeService'], 'applyThemePreference');

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
});
