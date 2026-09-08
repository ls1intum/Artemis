import { Injectable, inject } from '@angular/core';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { WINDOW_INJECTOR_TOKEN } from 'app/core/interceptor/artemis-version.interceptor';

/** Prefix for the per-url marker that records an attempted recovery, so a reload loop cannot form. */
export const LAZY_ROUTE_RECOVERY_KEY_PREFIX = 'artemis.lazyRouteRecovery.';

/**
 * Messages browsers use when a lazily imported route chunk cannot be fetched. Angular surfaces the raw import
 * failure on the {@code NavigationError}, so there is no error type to match on.
 */
const CHUNK_LOAD_FAILURE_PATTERNS = [
    /failed to fetch dynamically imported module/i,
    /error loading dynamically imported module/i,
    /importing a module script failed/i,
    /loading chunk \S+ failed/i,
];

/**
 * Recovers from a navigation that failed because the route's lazily loaded chunk could not be fetched.
 * <p>
 * Without this, such a navigation is silent: the router reports a {@code NavigationError}, the caller has usually
 * discarded the promise, and the application simply stays where it was. The user sees a click that did nothing,
 * which is what an instructor gets when a deployment replaces the chunk files while their tab is open.
 * <p>
 * The first failure for a url is answered with a full page load of that same url. That is deliberately not a
 * router navigation: a failed dynamic import stays failed in the module map, so only a fresh document re-fetches
 * the chunk, and after a deployment only a fresh document gets the new chunk names at all. The user asked to
 * leave the current page by clicking, so navigating there is also the least surprising recovery.
 * <p>
 * A marker in session storage makes that recovery one-shot per url. Should the reloaded document fail on the same
 * url again, the chunk is genuinely gone rather than momentarily unreachable, and the user is told instead.
 */
@Injectable({ providedIn: 'root' })
export class LazyRouteRecoveryService {
    private readonly injectedWindow = inject<Window>(WINDOW_INJECTOR_TOKEN);
    private readonly alertService = inject(AlertService);

    /**
     * Recovers from, or reports, a failed navigation. Anything that is not a chunk load failure is left alone, so
     * that guard rejections and server errors keep reaching their existing handling.
     *
     * @param error the error the router reported on the {@code NavigationError}
     * @param url   the url the navigation was heading for
     */
    handleNavigationError(error: unknown, url: string): void {
        if (!LazyRouteRecoveryService.isChunkLoadFailure(error)) {
            return;
        }

        if (this.hasAlreadyAttemptedRecovery(url)) {
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: 'artemisApp.lazyRouteLoadFailedAlert',
                timeout: 0,
                action: {
                    label: 'artemisApp.lazyRouteLoadFailedAction',
                    callback: () => this.injectedWindow.location.assign(url),
                },
            });
            return;
        }

        this.rememberRecoveryAttempt(url);
        this.injectedWindow.location.assign(url);
    }

    private static isChunkLoadFailure(error: unknown): boolean {
        if (error === null || error === undefined) {
            return false;
        }
        const candidate = error as { name?: unknown; message?: unknown };
        if (candidate.name === 'ChunkLoadError') {
            return true;
        }
        const message = typeof candidate.message === 'string' ? candidate.message : '';
        return CHUNK_LOAD_FAILURE_PATTERNS.some((pattern) => pattern.test(message));
    }

    /**
     * Session storage is read through a try/catch because a browser configured to block site data throws on access
     * rather than returning null. Recovering twice is a far better failure mode than never recovering at all, so an
     * unreadable store is treated as "not attempted yet".
     */
    private hasAlreadyAttemptedRecovery(url: string): boolean {
        try {
            return this.injectedWindow.sessionStorage.getItem(LAZY_ROUTE_RECOVERY_KEY_PREFIX + url) !== null;
        } catch {
            return false;
        }
    }

    private rememberRecoveryAttempt(url: string): void {
        try {
            this.injectedWindow.sessionStorage.setItem(LAZY_ROUTE_RECOVERY_KEY_PREFIX + url, String(Date.now()));
        } catch {
            // Nothing to do: the recovery below still runs, it just cannot be limited to one attempt.
        }
    }
}
