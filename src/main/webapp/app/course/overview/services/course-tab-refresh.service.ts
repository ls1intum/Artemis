import { Injectable } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable, Subject, filter, map } from 'rxjs';

/**
 * Tells a course tab when the user has selected it again while already on it.
 *
 * Driven by the sidebar reporting the click, not by watching the router. Inferring it from navigations does not work:
 * a tab navigates to its own URL while rendering — the communication tab does so as it resolves which conversation to
 * show — and a router-driven refresh cannot tell that apart from a user clicking the tab. Doing so made the tab refresh
 * itself in an unbounded request loop.
 *
 * Selecting a different tab destroys the current tab component and creates the new one, so that tab loads its data
 * anyway. Only re-selecting the tab you are already on needs this signal.
 */
@Injectable({ providedIn: 'root' })
export class CourseTabRefreshService {
    private readonly tabSelections = new Subject<string>();

    /**
     * Reports that the user clicked a sidebar tab.
     *
     * @param routerLink the link of the clicked tab, as the sidebar holds it
     */
    notifyTabSelected(routerLink: string): void {
        this.tabSelections.next(this.normalise(routerLink));
    }

    /**
     * Emits every time the user selects any sidebar tab.
     *
     * Used by the course container to reconcile which tabs exist once per selection. It cannot infer that from the
     * router either: a guard that denies a removed tab cancels its navigation and redirects, and the redirect is a
     * fresh navigation that looks like any other.
     *
     * @return an observable that emits on every tab selection
     */
    selections(): Observable<void> {
        return this.tabSelections.pipe(map(() => undefined));
    }

    /**
     * Emits every time the user selects the given tab.
     *
     * @param route the tab component's own activated route, whose URL the sidebar link points at
     * @return an observable that emits on every selection of this tab
     */
    reselections(route: ActivatedRoute): Observable<void> {
        const tabUrl = this.tabUrlOf(route);
        return this.tabSelections.pipe(
            filter((selected) => selected === tabUrl),
            map(() => undefined),
        );
    }

    /**
     * The segment that identifies a tab, for example `lectures`.
     *
     * Compared on the segment rather than the whole URL because sidebar links are relative — `communication`, or
     * `{courseId}/lectures` in the management sidebar — while the route knows its absolute path. Taking the last
     * segment of each puts them on equal terms.
     *
     * Read from the route's ancestry rather than its own segments, because some tabs route through an empty-path child
     * whose own URL is empty; and from the route rather than the current URL, so a tab showing a child — a single
     * lecture, say — still recognises a click on its own link.
     */
    private tabUrlOf(route: ActivatedRoute): string {
        // Angular always populates pathFromRoot; the fallback is for partial ActivatedRoute doubles in tests
        const segments = (route.pathFromRoot ?? []).flatMap((ancestor) => ancestor.snapshot.url.map((segment) => segment.path));
        return this.normalise(segments.at(-1) ?? '');
    }

    /** Reduces a link or path to the segment that names the tab. */
    private normalise(url: string): string {
        const path = url.split(/[?#]/)[0];
        return path.split('/').filter(Boolean).at(-1) ?? '';
    }
}
