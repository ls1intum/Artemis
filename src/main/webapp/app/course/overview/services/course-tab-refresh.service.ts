import { Injectable, inject } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
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
     * Emits every time the user re-selects the given tab.
     *
     * Must be called during the navigation that opens the tab — from a component constructor or `ngOnInit` — because
     * that navigation is excluded: the component loads once on its own, and emitting for it would load twice.
     *
     * @param route the tab component's own activated route, whose URL the sidebar link points at
     * @return an observable that emits on every re-selection of this tab
     */
    reselections(route: ActivatedRoute): Observable<void> {
        const openingNavigationId = this.router.currentNavigation()?.id;
        const tabUrl = this.tabUrlOf(route);

        return this.router.events.pipe(
            filter((event): event is NavigationEnd => event instanceof NavigationEnd),
            filter((event) => event.id !== openingNavigationId && this.pathOf(event.urlAfterRedirects) === tabUrl),
            map(() => undefined),
        );
    }

    /**
     * The URL of the tab itself, without whatever child route happens to be open.
     *
     * Deriving this from the route rather than from the navigation that created the component matters for deep links:
     * opening `/courses/1/lectures/7` directly creates the lectures tab too, and taking that navigation's URL would
     * leave the tab waiting for a re-selection of a URL the sidebar link never produces.
     */
    private tabUrlOf(route: ActivatedRoute): string {
        const segments = route.pathFromRoot.flatMap((ancestor) => ancestor.snapshot.url.map((segment) => segment.path));
        return '/' + segments.join('/');
    }

    /** Drops query parameters and the fragment, which do not decide whether this is the same tab. */
    private pathOf(url: string): string {
        return url.split(/[?#]/)[0];
    }
}
