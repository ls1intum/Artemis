import { Injectable, inject } from '@angular/core';
import { Router, isActive } from '@angular/router';
import { Observable, Subject } from 'rxjs';
import { LectureDeepLink, lectureDeepLinkQueryParams } from 'app/lecture/overview/course-lectures/lecture-deep-link.model';

/**
 * Carries a jump into a lecture unit from wherever it is triggered — an Iris citation, a global search result — to the
 * lecture page that executes it.
 *
 * A jump into a lecture the user is not on is a page change and goes through the router, with the deep link in the
 * query parameters so that the arriving page finds it. A jump inside the lecture already on screen is not navigation at
 * all: it is a message between two components that have no parent in common, and routing it through the URL would push
 * a history entry onto an identical one, leaving the student with a Back press that visibly does nothing. Such a jump
 * is therefore handed over directly and never touches the URL.
 *
 * Producers do not decide which of the two it is — {@link jump} does, so that the distinction cannot be forgotten at a
 * call site.
 */
@Injectable({ providedIn: 'root' })
export class LectureDeepLinkService {
    private readonly router = inject(Router);
    private readonly requestSource = new Subject<LectureDeepLink>();

    /**
     * Jumps issued while the lecture they point into is already on screen.
     *
     * A plain Subject on purpose: a jump is a command, so a page that subscribes later must not be handed one that was
     * executed long ago.
     */
    readonly requests: Observable<LectureDeepLink> = this.requestSource.asObservable();

    /**
     * Jumps to a place inside a lecture unit, navigating only if that lecture is not the page already open.
     *
     * Pass the route that is actually navigated to: search results carry a link built outside Artemis, and only that
     * link says where the jump really goes. A target that names no place inside the lecture — a search result without
     * unit parameters — is just that lecture, so it opens the page and has nothing to hand over once there.
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
