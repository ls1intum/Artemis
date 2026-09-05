import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Route } from '@angular/router';
import { Observable, of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { IdlePreloadScheduler } from 'app/core/config/idle-preload.scheduler';
import { RoleAwarePreloadingStrategy, preloadTierForRoute } from 'app/core/config/role-aware-preloading.strategy';
import { IS_AT_LEAST_ADMIN, IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_STUDENT, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import routes from 'app/app.routes';
import { courseManagementRoutes } from 'app/course/manage/course-management.route';

/** Finds a top-level lazy parent by its exact path (throws if the config no longer contains it). */
function findAppLazyRoute(path: string): Route {
    const match = (routes as Route[]).find((route) => route.path === path && route.loadChildren);
    if (!match) {
        throw new Error(`Lazy route '${path}' not found in app.routes`);
    }
    return match;
}

/** Recursively collects every route that declares a `loadChildren` (i.e. every lazy parent) within a route subtree. */
function collectLazyParents(routeList: readonly Route[]): Route[] {
    const lazyParents: Route[] = [];
    const walk = (list: readonly Route[] | undefined): void => {
        for (const route of list ?? []) {
            if (route.loadChildren) {
                lazyParents.push(route);
            }
            walk(route.children);
        }
    };
    walk(routeList);
    return lazyParents;
}

describe('RoleAwarePreloadingStrategy', () => {
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
        expect(enqueue).toHaveBeenCalledOnce();
        expect(enqueue.mock.calls[0][1]).toBe(1);
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

    it('skips routes that explicitly declare an empty authorities array', () => {
        emitted(route({ authorities: [] }));
        expect(accountStub.hasAnyAuthorityDirect).not.toHaveBeenCalled();
        expect(enqueue).not.toHaveBeenCalled();
    });

    it('preloads authority-less lazy parents when opted in via preload:eager', () => {
        emitted(route({ usesModuleBackground: true, preload: 'eager' }));
        expect(enqueue).toHaveBeenCalledOnce();
        expect(enqueue.mock.calls[0][1]).toBe(0);
    });

    it('enqueues a given route only once across repeated preload passes (re-walk dedupe)', () => {
        const r = route({ authorities: IS_AT_LEAST_STUDENT });
        emitted(r);
        emitted(r);
        expect(enqueue).toHaveBeenCalledOnce();
        expect(enqueue.mock.calls[0][1]).toBe(1);
    });

    it('skips a route with a non-array authorities value (defensive)', () => {
        emitted(route({ authorities: 'ROLE_ADMIN' }));
        expect(accountStub.hasAnyAuthorityDirect).not.toHaveBeenCalled();
        expect(enqueue).not.toHaveBeenCalled();
    });

    it('does not load a queued route when the user loses eligibility before execution', () => {
        const loader = vi.fn(() => of(undefined));
        const r = route({ authorities: IS_AT_LEAST_ADMIN });
        strategy.preload(r, loader).subscribe();
        expect(enqueue).toHaveBeenCalledOnce();

        const guardedLoad = enqueue.mock.calls[0][0] as () => unknown;

        accountStub.hasAnyAuthorityDirect.mockReturnValue(false);
        let result: unknown = 'unset';
        (guardedLoad() as Observable<unknown>).subscribe((v: unknown) => (result = v));
        expect(loader).not.toHaveBeenCalled();
        expect(result).toBeNull();
    });

    it('does not load a queued route when the user logs out before execution', () => {
        const loader = vi.fn(() => of(undefined));
        const r = route({ authorities: IS_AT_LEAST_STUDENT });
        strategy.preload(r, loader).subscribe();
        expect(enqueue).toHaveBeenCalledOnce();

        const guardedLoad = enqueue.mock.calls[0][0] as () => unknown;

        accountStub.isAuthenticated.mockReturnValue(false);
        let result: unknown = 'unset';
        (guardedLoad() as Observable<unknown>).subscribe((v: unknown) => (result = v));
        expect(loader).not.toHaveBeenCalled();
        expect(result).toBeNull();
    });

    // Regression: the real `course-management` lazy parent (its access authorities live on its children) must warm
    // for eligible staff so its loadChildren runs and Angular recurses into the management subtree. Before it
    // declared preload-only authorities the strategy skipped it for everyone and the children were never enqueued.
    it('warms the real course-management lazy parent for eligible staff at the management tier', () => {
        accountStub.hasAnyAuthorityDirect.mockReturnValue(true);
        expect(emitted(findAppLazyRoute('course-management'))).toBeNull();
        expect(enqueue).toHaveBeenCalledOnce();
        expect(enqueue.mock.calls[0][1]).toBe(2);
    });

    it('prunes the real course-management lazy parent for a pure student', () => {
        accountStub.hasAnyAuthorityDirect.mockReturnValue(false);
        expect(emitted(findAppLazyRoute('course-management'))).toBeNull();
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
        expect(preloadTierForRoute(route({ authorities: [] }))).toBeUndefined();
    });

    it('puts management routes in tier 2', () => {
        expect(preloadTierForRoute(route({ authorities: IS_AT_LEAST_TUTOR }))).toBe(2);
        expect(preloadTierForRoute(route({ authorities: IS_AT_LEAST_INSTRUCTOR }))).toBe(2);
    });

    it('puts admin-only routes in tier 3', () => expect(preloadTierForRoute(route({ authorities: IS_AT_LEAST_ADMIN }))).toBe(3));

    it('assigns every management/student lazy parent in app.routes a defined preload tier (regression: no cold subtrees)', () => {
        // Each of these lazy parents carries preload-only authorities (and no canActivate) so its subtree is
        // discovered and warmed for eligible users. A regression that drops the authorities would make this
        // return undefined and silently stop the whole subtree from ever being preloaded.
        const expectedTierByPath: Record<string, number> = {
            'course-management': 2,
            'course-management/:courseId/exams': 2,
            'course-management/:courseId/programming-exercises/:exerciseId/code-editor': 2,
            courses: 1,
            'courses/:courseId/exams/:examId/exercises/:exerciseId/repository': 1,
        };
        for (const [path, tier] of Object.entries(expectedTierByPath)) {
            const parent = findAppLazyRoute(path);
            expect(parent.loadChildren, `${path} should be a lazy parent`).toBeDefined();
            expect(parent.canActivate, `${path} authorities must be preload-only (no access guard)`).toBeUndefined();
            expect(preloadTierForRoute(parent), `${path} preload tier`).toBe(tier);
        }
    });

    it('assigns every nested lazy parent inside courseManagementRoutes a defined preload tier (regression: no cold management subtrees)', () => {
        // Angular's preloader only recurses into a lazy parent's loadChildren once the parent itself warms. A nested
        // management parent that resolves to `undefined` here is skipped, so its whole subtree stays cold even for
        // eligible staff. Every lazy parent nested in courseManagementRoutes (lectures, tutorial-groups, plagiarism,
        // exams, the exercise-management path:'' subtrees, ...) must therefore carry preload-only authorities and no
        // access guard. A regression that drops the authorities from any of them fails here.
        const lazyParents = collectLazyParents(courseManagementRoutes);
        expect(lazyParents.length).toBeGreaterThan(0);
        for (const parent of lazyParents) {
            const label = parent.path ? parent.path : '(empty path)';
            expect(preloadTierForRoute(parent), `${label} preload tier`).toBeDefined();
            expect(parent.canActivate, `${label} authorities must be preload-only (no access guard)`).toBeUndefined();
        }
    });
});
