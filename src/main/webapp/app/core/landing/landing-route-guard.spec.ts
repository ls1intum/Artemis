import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { inject } from '@angular/core';

@Component({ template: '', standalone: true })
class DummyComponent {}

/**
 * Extracted root route guard matching the one in app.routes.ts.
 * Authenticated users are redirected to /courses; unauthenticated users see the landing page.
 */
const rootRouteGuard = (): Promise<boolean | UrlTree> => {
    const accountService = inject(AccountService);
    const router = inject(Router);
    return accountService
        .identity()
        .then((account) => {
            if (account) {
                return router.parseUrl('/courses');
            }
            return true;
        })
        .catch(() => true);
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
        vi.spyOn(accountService, 'identity').mockResolvedValue({ id: 1, login: 'user' } as User);

        await router.navigateByUrl('/');

        expect(router.url).toBe('/courses');
    });

    it('should allow unauthenticated users to access the landing page', async () => {
        vi.spyOn(accountService, 'identity').mockResolvedValue(undefined);

        await router.navigateByUrl('/');

        expect(router.url).toBe('/');
    });

    it('should allow access when identity call fails', async () => {
        vi.spyOn(accountService, 'identity').mockRejectedValue(new Error('Network error'));

        await router.navigateByUrl('/');

        expect(router.url).toBe('/');
    });
});
