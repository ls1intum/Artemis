import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { HomeComponent } from 'app/core/home/home.component';
import { Lti13ExerciseLaunchComponent } from './lti13-exercise-launch.component';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { LoginService } from 'app/core/login/login.service';
import { Credentials } from 'app/core/auth/auth-jwt.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { WebauthnService } from 'app/account/user/settings/passkey-settings/webauthn.service';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

@Component({ template: '' })
class DestinationComponent {}

describe('LTI login continuation', () => {
    const user = { id: 1, login: 'student' } as User;
    const target = '/courses/1/exercises/2?isMultiLaunch=true&view=feedback#result';
    let router: Router;
    let storage: SessionStorageService;
    let authState: BehaviorSubject<User | undefined>;
    let allowTarget: boolean;
    let identity: ReturnType<typeof vi.fn>;
    let http: HttpTestingController;

    beforeEach(async () => {
        sessionStorage.clear();
        allowTarget = true;
        identity = vi.fn(() => Promise.resolve(user));
        authState = new BehaviorSubject<User | undefined>(undefined);
        await TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideLocationMocks(),
                provideRouter([
                    { path: 'sign-in', component: DestinationComponent },
                    { path: 'courses', component: DestinationComponent },
                    { path: 'stale', component: DestinationComponent },
                    { path: 'courses/:courseId/exercises/:exerciseId', component: DestinationComponent, canActivate: [() => allowTarget] },
                ]),
                {
                    provide: AccountService,
                    useValue: {
                        userIdentity: signal(undefined),
                        identity,
                        authenticate: vi.fn(),
                        getAuthenticationState: () => authState.asObservable(),
                        getAndClearPrefilledUsername: () => undefined,
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(AlertService),
                MockProvider(WebauthnService),
                MockProvider(ThemeService),
            ],
        }).overrideComponent(HomeComponent, { set: { template: '', imports: [] } });
        router = TestBed.inject(Router);
        storage = TestBed.inject(SessionStorageService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        http.verify();
        sessionStorage.clear();
        vi.restoreAllMocks();
    });

    function startLaunch() {
        const fixture = TestBed.createComponent(Lti13ExerciseLaunchComponent);
        fixture.componentInstance.redirectUserToLoginThenTargetLink(
            new HttpErrorResponse({
                status: 401,
                error: { targetLinkUri: `${window.location.origin}${target}`, ltiIdToken: 'launch-token', clientRegistrationId: 'moodle' },
            }),
        );
        return fixture;
    }

    it('persists the target and launch metadata before leaving for sign-in', async () => {
        startLaunch();
        await vi.waitFor(() => expect(router.url).toBe('/sign-in'));
        expect(storage.retrieve('ltiPendingLaunch')).toEqual({ url: target, ltiIdToken: 'launch-token', clientRegistrationId: 'moodle' });
    });

    it('continues the launch after a wrong password followed by a successful password login', async () => {
        startLaunch();
        await vi.waitFor(() => expect(router.url).toBe('/sign-in'));
        storage.store('authenticationToken', 'stale-authentication');
        const login = TestBed.inject(LoginService);
        const failedLogin = login.login(new Credentials('student', 'wrong-password', false));
        http.expectOne('api/core/public/authenticate').flush({}, { status: 401, statusText: 'Unauthorized' });
        await expect(failedLogin).rejects.toMatchObject({ status: 401 });
        expect(storage.retrieve('authenticationToken')).toBeUndefined();

        const successfulLogin = login.login(new Credentials('student', 'correct-password', false));
        http.expectOne('api/core/public/authenticate').flush({});
        await successfulLogin;
        TestBed.createComponent(HomeComponent).componentInstance.currentUserCallback(user);

        await vi.waitFor(() => expect(router.url).toBe(target));
        expect(storage.retrieve('ltiPendingLaunch')).toBeUndefined();
        expect(storage.retrieve('ltiIdToken')).toBe('launch-token');
        expect(storage.retrieve('clientRegistrationId')).toBe('moodle');
    });

    it('discards a pending launch on an explicit logout', async () => {
        startLaunch();
        await vi.waitFor(() => expect(router.url).toBe('/sign-in'));

        TestBed.inject(LoginService).logout(true);
        http.expectOne('api/core/public/logout').flush({});

        expect(storage.retrieve('ltiPendingLaunch')).toBeUndefined();
    });

    it('retains a cancelled continuation for the next successful sign-in handoff', async () => {
        startLaunch();
        await vi.waitFor(() => expect(router.url).toBe('/sign-in'));
        allowTarget = false;
        const home = TestBed.createComponent(HomeComponent).componentInstance;
        home.currentUserCallback(user);
        await new Promise((resolve) => setTimeout(resolve, 0));
        expect(router.url).toBe('/sign-in');
        expect(storage.retrieve('ltiPendingLaunch')).toBeDefined();

        allowTarget = true;
        home.currentUserCallback(user);
        await vi.waitFor(() => expect(router.url).toBe(target));
        expect(storage.retrieve('ltiPendingLaunch')).toBeUndefined();
    });

    it.each(['https://other.example/courses/1/exercises/2', '//other.example/courses/1/exercises/2', 'javascript:alert(1)'])(
        'does not persist or navigate an unsafe launch target %s',
        async (targetLinkUri) => {
            const launch = TestBed.createComponent(Lti13ExerciseLaunchComponent).componentInstance;
            launch.redirectUserToLoginThenTargetLink(
                new HttpErrorResponse({
                    status: 401,
                    error: { targetLinkUri, ltiIdToken: 'launch-token', clientRegistrationId: 'moodle' },
                }),
            );
            await new Promise((resolve) => setTimeout(resolve, 0));
            expect(storage.retrieve('ltiPendingLaunch')).toBeUndefined();
            expect(router.url).not.toBe('/sign-in');
        },
    );

    it('resumes once after a page round trip, ahead of stale previousUrl and delayed auth state', async () => {
        storage.store('previousUrl', '/stale');
        const launch = startLaunch();
        await vi.waitFor(() => expect(router.url).toBe('/sign-in'));
        launch.destroy();
        let finishInitialIdentity!: (account: User) => void;
        identity.mockReturnValueOnce(new Promise<User>((resolve) => (finishInitialIdentity = resolve)));
        const homeFixture = TestBed.createComponent(HomeComponent);
        homeFixture.detectChanges();
        TestBed.inject(EventManager).broadcast({ name: 'authenticationSuccess', content: 'Logged in' });
        await vi.waitFor(() => expect(router.url).toBe(target));

        // The initial lookup finishes after the login event's navigation. It must not navigate to /courses.
        finishInitialIdentity(user);
        await homeFixture.whenStable();
        expect(router.url).toBe(target);
        await router.navigateByUrl('/courses');
        homeFixture.componentInstance.currentUserCallback(user);
        await homeFixture.whenStable();
        expect(router.url).toBe('/courses');
        expect(storage.retrieve('ltiPendingLaunch')).toBeUndefined();
        expect(storage.retrieve('ltiIdToken')).toBe('launch-token');
        expect(storage.retrieve('clientRegistrationId')).toBe('moodle');
    });
});
