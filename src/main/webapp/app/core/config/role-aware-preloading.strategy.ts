import { Injectable, inject } from '@angular/core';
import { PreloadingStrategy, Route } from '@angular/router';
import { Observable, of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { Authority } from 'app/foundation/constants/authority.constants';
import { IdlePreloadScheduler } from 'app/core/config/idle-preload.scheduler';

/**
 * Optional `route.data.preload` hint:
 * - `'eager'`  — warm this route in the first tier, ahead of the role-derived ones.
 * - `'never'`  — never warm this route in the background; keep it strictly on-demand (use for very large,
 *                rarely-used chunks such as the PDF viewer iframe).
 */
export type RoutePreloadHint = 'eager' | 'never';

/** Priority tiers, drained low-to-high by {@link IdlePreloadScheduler}. */
const TIER_EAGER = 0;
const TIER_STUDENT = 1;
const TIER_MANAGEMENT = 2;
const TIER_ADMIN = 3;

/**
 * Derives the warming priority of a lazy route from the authorities it requires. The `IS_AT_LEAST_*` arrays are
 * nested (each higher role is also allowed), so the *least* privileged role still permitted is what distinguishes
 * a student route from a management or admin route. Routes without authorities (e.g. lazy route-config parents
 * whose authorities live on their children) default to the student tier so the strategy descends into them.
 */
export function preloadTierForRoute(route: Route): number {
    if (route.data?.['preload'] === 'eager') {
        return TIER_EAGER;
    }
    const authorities = route.data?.['authorities'] as readonly Authority[] | undefined;
    if (!authorities || authorities.includes(Authority.STUDENT)) {
        return TIER_STUDENT;
    }
    if (authorities.includes(Authority.TUTOR) || authorities.includes(Authority.EDITOR) || authorities.includes(Authority.INSTRUCTOR)) {
        return TIER_MANAGEMENT;
    }
    return TIER_ADMIN;
}

/**
 * Role-aware, idle-staged route preloading. After the app settles, lazy route chunks the current user can
 * actually reach are warmed in the background (student routes first, then management, then admin), so later
 * navigation is instant — while a pure student never downloads management or admin code at all.
 *
 * The strategy only decides *eligibility* and *priority* here and returns `of(null)` to Angular's preloader
 * (so nothing is fetched on the critical path); {@link IdlePreloadScheduler} owns the actual `load()` calls and
 * their timing. Gating mirrors {@link file://../auth/user-route-access-service.ts}: the required authorities are
 * read straight off `route.data['authorities']`. Routes whose authorities the user lacks are skipped, which —
 * because returning `of(null)` stops Angular recursing into them — prunes the entire inaccessible subtree.
 *
 * Wired up via `withPreloading(RoleAwarePreloadingStrategy)` in {@link file://../../app.config.ts}.
 */
@Injectable({ providedIn: 'root' })
export class RoleAwarePreloadingStrategy implements PreloadingStrategy {
    private readonly accountService = inject(AccountService);
    private readonly scheduler = inject(IdlePreloadScheduler);

    preload(route: Route, load: () => Observable<unknown>): Observable<unknown> {
        if (route.data?.['preload'] === 'never') {
            return of(null);
        }
        // Warm only for an authenticated user; an anonymous visitor would pull code they cannot use. After login
        // the preloader re-walks on the next NavigationEnd, so warming starts then (also covers a page refresh).
        if (!this.accountService.isAuthenticated()) {
            return of(null);
        }
        const authorities = route.data?.['authorities'] as readonly Authority[] | undefined;
        if (authorities && authorities.length > 0 && !this.accountService.hasAnyAuthorityDirect(authorities)) {
            return of(null);
        }
        this.scheduler.enqueue(load, preloadTierForRoute(route));
        return of(null);
    }
}
