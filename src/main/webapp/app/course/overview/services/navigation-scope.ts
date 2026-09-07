import { Router } from '@angular/router';

/**
 * Identifies the navigation that course tab data belongs to.
 *
 * Course tab data is deliberately not cached across navigations: selecting a tab — including selecting the tab you are
 * already on, which acts as a refresh — must show what the server has now, not what it had when the course was entered.
 * Keeping a response beyond its navigation is what leaves a client stale after a release, and no eviction rule reliably
 * fixes that.
 *
 * Within a single navigation the opposite holds. The route guard and the sidebar both decide from the available tabs,
 * and the guard necessarily runs before the container exists, so they ask one after the other rather than together.
 * Asking the server twice, milliseconds apart, buys no freshness. Comparing navigation ids draws the line exactly
 * where it belongs: everything that happens because of one tab selection shares one response, and the next selection
 * starts over.
 *
 * Both router properties matter. A route guard runs while its navigation is still in flight and sees it as the current
 * one; a component that loads just after activation sees the same navigation as the last successful one. Falling back
 * to 0 means no navigation has happened yet, which only occurs before the first one completes.
 *
 * @param router the application router
 * @return the id of the navigation in progress, or of the last completed one
 */
export function currentNavigationId(router: Router): number {
    return router.currentNavigation()?.id ?? router.lastSuccessfulNavigation()?.id ?? 0;
}
