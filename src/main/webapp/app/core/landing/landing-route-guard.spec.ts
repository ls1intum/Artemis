import { Component, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';

@Component({ template: '', standalone: true })
class DummyComponent {}

/**
 * Extracted root route guard mirroring the one in app.routes.ts.
 * The APP_INITIALIZER resolves the user identity (and, when returning from a SAML2 IdP, also
 * completes the second-step JWT exchange) before this guard runs, so a synchronous read on
 * userIdentity() is enough.
 */
const rootRouteGuard = (): boolean | UrlTree => {
    const accountService = inject(AccountService);
    const router = inject(Router);
    if (accountService.userIdentity()) {
        return router.parseUrl('/courses');
    }
    return true;
};

describe('Landing page route guard', () => {
    setupTestBed({ zoneless: true });

    let accountService: AccountService;
    let router: Router;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                provideRouter([
                    { path: '', component: DummyComponent, canActivate: [rootRouteGuard] },
                    { path: 'courses', component: DummyComponent },
                ]),
                { provide: AccountService, useClass: MockAccountService },
            ],
        }).compileComponents();

        accountService = TestBed.inject(AccountService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should redirect authenticated users to /courses', async () => {
        accountService.userIdentity.set({ id: 1, login: 'user' } as User);

        await router.navigateByUrl('/');

        expect(router.url).toBe('/courses');
    });

    it('should allow unauthenticated users to access the landing page', async () => {
        accountService.userIdentity.set(undefined);

        await router.navigateByUrl('/');

        expect(router.url).toBe('/');
    });
});
