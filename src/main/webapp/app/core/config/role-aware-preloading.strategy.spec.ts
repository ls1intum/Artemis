import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { Route } from '@angular/router';
import { of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { IdlePreloadScheduler } from 'app/core/config/idle-preload.scheduler';
import { RoleAwarePreloadingStrategy, preloadTierForRoute } from 'app/core/config/role-aware-preloading.strategy';
import { IS_AT_LEAST_ADMIN, IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_STUDENT, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';

describe('RoleAwarePreloadingStrategy', () => {
    setupTestBed({ zoneless: true });

    let strategy: RoleAwarePreloadingStrategy;
    const enqueue = vi.fn();
    const accountStub = { isAuthenticated: vi.fn(() => true), hasAnyAuthorityDirect: vi.fn(() => true) };
    const load = () => of(undefined);
    const route = (data?: Record<string, unknown>): Route => ({ path: 'x', data });

    beforeEach(() => {
        enqueue.mockReset();
        accountStub.isAuthenticated.mockReset().mockReturnValue(true);
        accountStub.hasAnyAuthorityDirect.mockReset().mockReturnValue(true);
        TestBed.configureTestingModule({
            providers: [RoleAwarePreloadingStrategy, { provide: AccountService, useValue: accountStub }, { provide: IdlePreloadScheduler, useValue: { enqueue } }],
        });
        strategy = TestBed.inject(RoleAwarePreloadingStrategy);
    });

    afterEach(() => vi.restoreAllMocks());

    /** Runs the strategy and returns the synchronously-emitted value (always expected to be null). */
    function emitted(r: Route): unknown {
        let value: unknown = 'unset';
        strategy.preload(r, load).subscribe((v) => (value = v));
        return value;
    }

    it('enqueues an eligible route at its tier and never preloads on the critical path', () => {
        expect(emitted(route({ authorities: IS_AT_LEAST_STUDENT }))).toBeNull();
        expect(enqueue).toHaveBeenCalledExactlyOnceWith(load, 1);
    });

    it('prunes a route whose authorities the user lacks', () => {
        accountStub.hasAnyAuthorityDirect.mockReturnValue(false);
        expect(emitted(route({ authorities: IS_AT_LEAST_ADMIN }))).toBeNull();
        expect(enqueue).not.toHaveBeenCalled();
    });

    it('does not warm anything for an unauthenticated visitor', () => {
        accountStub.isAuthenticated.mockReturnValue(false);
        emitted(route({ authorities: IS_AT_LEAST_STUDENT }));
        expect(accountStub.hasAnyAuthorityDirect).not.toHaveBeenCalled();
        expect(enqueue).not.toHaveBeenCalled();
    });

    it('never warms a route hinted preload:never, even when eligible', () => {
        emitted(route({ authorities: IS_AT_LEAST_STUDENT, preload: 'never' }));
        expect(enqueue).not.toHaveBeenCalled();
    });

    it('skips authority-less lazy parents unless explicitly opted in', () => {
        emitted(route({ usesModuleBackground: true }));
        expect(enqueue).not.toHaveBeenCalled();
    });

    it('preloads authority-less lazy parents when opted in via preload:eager', () => {
        emitted(route({ usesModuleBackground: true, preload: 'eager' }));
        expect(enqueue).toHaveBeenCalledExactlyOnceWith(load, 0);
    });

    it('enqueues a given route only once across repeated preload passes (re-walk dedupe)', () => {
        const r = route({ authorities: IS_AT_LEAST_STUDENT });
        emitted(r);
        emitted(r);
        expect(enqueue).toHaveBeenCalledExactlyOnceWith(load, 1);
    });

    it('skips a route with a non-array authorities value (defensive)', () => {
        emitted(route({ authorities: 'ROLE_ADMIN' }));
        expect(accountStub.hasAnyAuthorityDirect).not.toHaveBeenCalled();
        expect(enqueue).not.toHaveBeenCalled();
    });
});

describe('preloadTierForRoute', () => {
    const route = (data?: Record<string, unknown>): Route => ({ path: 'x', data });

    it('puts the eager hint in tier 0', () => expect(preloadTierForRoute(route({ preload: 'eager' }))).toBe(0));

    it('puts student routes in tier 1', () => {
        expect(preloadTierForRoute(route({ authorities: IS_AT_LEAST_STUDENT }))).toBe(1);
    });

    it('returns undefined for authority-less routes (requires explicit opt-in)', () => {
        expect(preloadTierForRoute(route())).toBeUndefined();
    });

    it('puts management routes in tier 2', () => {
        expect(preloadTierForRoute(route({ authorities: IS_AT_LEAST_TUTOR }))).toBe(2);
        expect(preloadTierForRoute(route({ authorities: IS_AT_LEAST_INSTRUCTOR }))).toBe(2);
    });

    it('puts admin-only routes in tier 3', () => expect(preloadTierForRoute(route({ authorities: IS_AT_LEAST_ADMIN }))).toBe(3));
});
