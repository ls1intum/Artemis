import { Injectable, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, filter, map } from 'rxjs';

/**
 * Tells a course tab when the user has selected it again while already on it.
 *
 * Selecting a different tab destroys the current tab component and creates the new one, so that tab loads its data
 * anyway. Selecting the tab you are already on is the case the router would otherwise swallow: the component is
 * reused, so nothing re-runs. Artemis enables `onSameUrlNavigation: 'reload'`, which makes the router emit a fresh
 * navigation for the identical URL, and this service turns that into a refresh signal.
 *
 * Navigating into a child route — opening a single lecture from the lectures tab — is deliberately not a refresh: the
 * tab component stays alive, but the user selected a lecture rather than the tab.
 */
@Injectable({ providedIn: 'root' })
export class CourseTabRefreshService {
    private readonly router = inject(Router);

    /**
     * Emits every time the user re-selects the tab that is being opened by the navigation in progress.
     *
     * Must be called from an injection context during that navigation — a component constructor or field initialiser —
     * because it identifies the tab from the navigation that is creating the component. The navigation that opens the
     * tab never emits: the component loads once on its own.
     *
     * @return an observable that emits on every re-selection of this tab
     */
    reselections(): Observable<void> {
        const openingNavigation = this.router.currentNavigation();
        const openingNavigationId = openingNavigation?.id;
        const tabUrl = openingNavigation?.finalUrl ? this.router.serializeUrl(openingNavigation.finalUrl) : this.router.url;

        return this.router.events.pipe(
            filter((event): event is NavigationEnd => event instanceof NavigationEnd),
            filter((event) => event.id !== openingNavigationId && event.urlAfterRedirects === tabUrl),
            map(() => undefined),
        );
    }
}
