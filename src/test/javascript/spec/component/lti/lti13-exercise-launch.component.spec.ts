import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { Lti13ExerciseLaunchComponent } from 'app/lti/lti13-exercise-launch.component';
import { ActivatedRoute, ActivatedRouteSnapshot, Router, convertToParamMap } from '@angular/router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { LoginService } from 'app/core/login/login.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';

describe('Lti13ExerciseLaunchComponent', () => {
    let fixture: ComponentFixture<Lti13ExerciseLaunchComponent>;
    let comp: Lti13ExerciseLaunchComponent;
    let route: ActivatedRoute;
    let http: HttpClient;
    let loginService: LoginService;
    let accountService: AccountService;
    const mockRouter = {
        navigate: jest.fn(() => Promise.resolve(true)),
    } as unknown as Router;
    const navigateSpy = jest.spyOn(mockRouter, 'navigate');

    beforeEach(() => {
        route = {
            snapshot: { queryParamMap: convertToParamMap({ state: 'state', id_token: 'id_token' }) },
        } as ActivatedRoute;

        window.sessionStorage.setItem('state', 'state');

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LoginService, useValue: loginService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useValue: mockRouter },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(Lti13ExerciseLaunchComponent);
                comp = fixture.componentInstance;
                accountService = TestBed.inject(AccountService);
            });

        http = TestBed.inject(HttpClient);
    });

    afterEach(() => {
        window.sessionStorage.clear();
        jest.restoreAllMocks();
        navigateSpy.mockClear();
    });

    it('onInit fail without state', () => {
        const httpStub = jest.spyOn(http, 'post');
        const consoleSpy = jest.spyOn(console, 'error').mockImplementation();

        route.snapshot = { queryParamMap: convertToParamMap({ id_token: 'id_token' }) } as ActivatedRouteSnapshot;

        comp.ngOnInit();

        expect(consoleSpy).toHaveBeenCalledOnce();
        expect(consoleSpy).toHaveBeenCalledWith('Required parameter for LTI launch missing');
        expect(comp.isLaunching).toBeFalse();
        expect(httpStub).not.toHaveBeenCalled();
    });

    it('onInit fail without token', () => {
        const httpStub = jest.spyOn(http, 'post');
        const consoleSpy = jest.spyOn(console, 'error').mockImplementation();

        route.snapshot = { queryParamMap: convertToParamMap({ state: 'state' }) } as ActivatedRouteSnapshot;

        comp.ngOnInit();

        expect(consoleSpy).toHaveBeenCalledOnce();
        expect(consoleSpy).toHaveBeenCalledWith('Required parameter for LTI launch missing');
        expect(comp.isLaunching).toBeFalse();
        expect(httpStub).not.toHaveBeenCalled();
    });

    it('onInit no targetLinkUri', () => {
        const httpStub = jest.spyOn(http, 'post').mockReturnValue(of({ ltiIdToken: 'id-token', clientRegistrationId: 'client-id' }));
        const consoleSpy = jest.spyOn(console, 'error').mockImplementation();

        expect(comp.isLaunching).toBeTrue();

        comp.ngOnInit();

        expect(consoleSpy).toHaveBeenCalled();
        expect(consoleSpy).toHaveBeenCalledWith('No LTI targetLinkUri received for a successful launch');
        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/public/lti13/auth-login', expect.anything(), expect.anything());

        expect(comp.isLaunching).toBeFalse();
    });

    it('onInit success to call launch endpoint', () => {
        const targetLink = window.location.host + '/targetLink';
        const httpStub = jest.spyOn(http, 'post').mockReturnValue(of({ targetLinkUri: targetLink, ltiIdToken: 'id-token', clientRegistrationId: 'client-id' }));

        expect(comp.isLaunching).toBeTrue();

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/public/lti13/auth-login', expect.anything(), expect.anything());
    });

    it('onInit launch fails on error', () => {
        const httpStub = simulateLtiLaunchError(http, 400);

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/public/lti13/auth-login', expect.anything(), expect.anything());

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

        expect(httpStub).toHaveBeenCalledWith('api/public/lti13/auth-login', expect.anything(), expect.anything());
        expect(comp.authenticateUserThenRedirect).toHaveBeenCalled();
        expect(identitySpy).toHaveBeenCalled();
        expect(comp.redirectUserToLoginThenTargetLink).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalledWith(['/']);
        expect(authStateSpy).toHaveBeenCalled();
    }));

    it('should redirect user to target link when user is already logged in', fakeAsync(() => {
        window.location.replace = jest.fn();
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
        expect(httpStub).toHaveBeenCalledWith('api/public/lti13/auth-login', expect.anything(), expect.anything());
        expect(navigateSpy).not.toHaveBeenCalled();
        expect(comp.redirectUserToTargetLink).toHaveBeenCalled();
    }));

    it('should redirect user to target link after user logged in', fakeAsync(() => {
        window.location.replace = jest.fn();
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
        expect(httpStub).toHaveBeenCalledWith('api/public/lti13/auth-login', expect.anything(), expect.anything());
        expect(navigateSpy).toHaveBeenCalled();
        expect(comp.redirectUserToLoginThenTargetLink).toHaveBeenCalled();
    }));

    function simulateLtiLaunchError(http: HttpClient, status: number, headers: any = {}, error = {}) {
        const httpStub = jest.spyOn(http, 'post').mockReturnValue(
            throwError(() => ({
                status,
                headers: { get: () => 'lti_user', ...headers },
                error: { targetLinkUri: 'mockTargetLinkUri', ...error },
            })),
        );
        return httpStub;
    }
});
