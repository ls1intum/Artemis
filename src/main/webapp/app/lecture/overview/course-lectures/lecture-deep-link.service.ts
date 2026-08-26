import { Injectable, inject } from '@angular/core';
import { Router, isActive } from '@angular/router';
import { Observable, Subject } from 'rxjs';
import { LectureDeepLink, lectureDeepLinkQueryParams } from 'app/lecture/overview/course-lectures/lecture-deep-link.model';

/**
 * Carries a jump into a lecture unit from an Iris citation or a search result to the lecture page that executes it.
 *
 * A jump into another lecture is a page change and goes through the router. A jump inside the lecture already on screen
 * is not navigation at all, and routing it through the URL would push a history entry onto an identical one — so it is
 * handed over directly. {@link jump} decides which, so a call site cannot forget the distinction.
 */
@Injectable({ providedIn: 'root' })
export class LectureDeepLinkService {
    private readonly router = inject(Router);
    private readonly requestSource = new Subject<LectureDeepLink>();

    /** A plain Subject: a jump is a command, so a page subscribing later must not be handed one already executed. */
    readonly requests: Observable<LectureDeepLink> = this.requestSource.asObservable();

    /**
     * Jumps to a place inside a lecture unit, navigating only if that lecture is not the page already open.
     *
     * Pass the route actually navigated to: search results carry a link built outside Artemis. A target naming no place
     * inside the lecture just opens it.
     */
    jump(lectureRoute: string | (string | number)[], deepLink?: LectureDeepLink): void {
        const commands = typeof lectureRoute === 'string' ? [lectureRoute] : lectureRoute;
        if (this.staysOnCurrentPage(commands)) {
            if (deepLink) {
                this.requestSource.next(deepLink);
            }
            return;
        }

        void this.router.navigate(commands, { queryParams: deepLink ? lectureDeepLinkQueryParams(deepLink) : {} });
    }

    /** Whether the target is the page on screen, judged without the query parameters — the jump itself is those. */
    private staysOnCurrentPage(commands: (string | number)[]): boolean {
        return isActive(this.router.createUrlTree(commands), this.router, {
            paths: 'exact',
            queryParams: 'ignored',
            fragment: 'ignored',
            matrixParams: 'ignored',
        })();
    }
}
