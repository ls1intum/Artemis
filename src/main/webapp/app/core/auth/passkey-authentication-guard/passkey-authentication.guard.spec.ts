import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, NavigationStart, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { MockProvider } from 'ng-mocks';
import { PasskeyAuthenticationGuard } from './passkey-authentication.guard';
import { AccountService } from 'app/core/auth/account.service';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';
import { User } from 'app/account/user/user.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { Authority, IS_AT_LEAST_ADMIN } from 'app/foundation/constants/authority.constants';

@Component({ template: '' })
class DummyComponent {}

describe('PasskeyAuthenticationGuard', () => {
    let guard: PasskeyAuthenticationGuard;
    let accountService: AccountService;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [PasskeyAuthenticationGuard, { provide: AccountService, useClass: MockAccountService }, provideHttpClient()],
        });
        guard = TestBed.inject(PasskeyAuthenticationGuard);
        accountService = TestBed.inject(AccountService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should allow activation when passkeys are not required for administrator features', async () => {
        vi.spyOn(accountService, 'isPasskeyRequiredForAdministratorFeatures').mockReturnValue(false);
        const approvedPasskeySpy = vi.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey');

        const result = await guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

        expect(result).toBe(true);
        expect(approvedPasskeySpy).not.toHaveBeenCalled();
    });

    it('should allow activation when user is logged in with approved passkey', async () => {
        vi.spyOn(accountService, 'isPasskeyRequiredForAdministratorFeatures').mockReturnValue(true);
        vi.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(true);
        vi.spyOn(accountService, 'identity').mockResolvedValue({ id: 99, login: 'admin' } as User);

        const result = await guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

        expect(result).toBe(true);
    });

    it('should redirect to passkey-required page when user is not logged in with approved passkey', async () => {
        vi.spyOn(accountService, 'isPasskeyRequiredForAdministratorFeatures').mockReturnValue(true);
        vi.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(false);
        vi.spyOn(accountService, 'identity').mockResolvedValue({ id: 99, login: 'admin' } as User);

        const mockState = { url: '/admin/user-management' } as RouterStateSnapshot;
        const result = await guard.canActivate({} as ActivatedRouteSnapshot, mockState);

        expect(result).toBeInstanceOf(UrlTree);
        expect(router.serializeUrl(result as UrlTree)).toBe('/passkey-required?returnUrl=%2Fadmin%2Fuser-management');
    });

    it('should pass the correct return URL in query parameters', async () => {
        vi.spyOn(accountService, 'isPasskeyRequiredForAdministratorFeatures').mockReturnValue(true);
        vi.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(false);
        vi.spyOn(accountService, 'identity').mockResolvedValue({ id: 99, login: 'admin' } as User);

        const mockState = { url: '/admin/metrics' } as RouterStateSnapshot;
        const result = await guard.canActivate({} as ActivatedRouteSnapshot, mockState);

        expect(result).toBeInstanceOf(UrlTree);
        const redirect = result as UrlTree;
        expect(redirect.root.children['primary'].segments.map((segment) => segment.path)).toEqual(['passkey-required']);
        expect(redirect.queryParams).toEqual({ returnUrl: '/admin/metrics' });
    });
});

// The admin routes list both guards in one canActivate array: [UserRouteAccessService, PasskeyAuthenticationGuard].
// The router runs them together and applies the first result that is not true, in array order, so the passkey
// redirect only takes effect once the user has passed the authority check.
describe('PasskeyAuthenticationGuard after UserRouteAccessService in one canActivate array', () => {
    let router: Router;
    let startedUrls: string[];

    /** Lets the promise chains of both guards, and any navigation they start, run to completion. */
    const settle = () => new Promise((resolve) => setTimeout(resolve));

    const configureFor = (account: User | undefined) => {
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            providers: [
                provideRouter([
                    { path: 'admin', component: DummyComponent, data: { authorities: IS_AT_LEAST_ADMIN }, canActivate: [UserRouteAccessService, PasskeyAuthenticationGuard] },
                    { path: 'passkey-required', component: DummyComponent },
                    { path: 'accessdenied', component: DummyComponent },
                    { path: 'sign-in', component: DummyComponent },
                    { path: 'courses', component: DummyComponent },
                ]),
                MockProvider(AlertService),
                MockProvider(SessionStorageService),
                // Passkey sign-in is required for administrator features, and the user has not signed in with a passkey.
                MockProvider(AccountService, {
                    identity: () => Promise.resolve(account),
                    hasAnyAuthority: (required: readonly Authority[]) => Promise.resolve(required.some((authority) => account?.authorities?.includes(authority) ?? false)),
                    isPasskeyRequiredForAdministratorFeatures: () => true,
                    isUserLoggedInWithApprovedPasskey: signal(false),
                }),
            ],
        });
        router = TestBed.inject(Router);
        startedUrls = [];
        router.events.subscribe((event) => {
            if (event instanceof NavigationStart) {
                startedUrls.push(event.url);
            }
        });
    };

    it('sends an administrator without a passkey sign-in to the passkey page', async () => {
        configureFor({ id: 1, login: 'admin', authorities: [Authority.ADMIN] } as User);
        await router.navigate(['/courses']);

        const navigated = await router.navigate(['/admin']);
        await settle();

        expect(navigated).toBe(true);
        expect(router.url).toBe('/passkey-required?returnUrl=%2Fadmin');
    });

    it('rejects a user without the administrator authority instead of sending them to the passkey page', async () => {
        configureFor({ id: 2, login: 'student', authorities: [Authority.STUDENT] } as User);
        await router.navigate(['/courses']);

        const navigated = await router.navigate(['/admin']);
        await settle();

        expect(navigated).toBe(false);
        expect(router.url).toBe('/courses');
        expect(startedUrls).toEqual(['/courses', '/admin']);
    });

    it('sends a user who is not signed in to the sign-in page, not to the passkey page', async () => {
        configureFor(undefined);
        await router.navigate(['/courses']);

        await router.navigate(['/admin']);
        await settle();

        expect(router.url).toBe('/sign-in');
        expect(startedUrls).not.toContain('/passkey-required?returnUrl=%2Fadmin');
    });
});
