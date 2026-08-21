import { Injectable, inject } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { Observable, filter, fromEvent, map, merge, share } from 'rxjs';

/**
 * Emits whenever the user is about to leave the current page: on tab/window close, navigating away within the
 * app, backgrounding the tab (visibility change), or losing window focus. Used by features that need a best-effort
 * signal to flush or clean up state before the page becomes unavailable.
 */
@Injectable({ providedIn: 'root' })
export class PageActivityService {
    private readonly router = inject(Router);

    readonly pageLeaving$: Observable<void> = merge(
        fromEvent(window, 'beforeunload'),
        fromEvent(document, 'visibilitychange').pipe(filter(() => document.visibilityState === 'hidden')),
        fromEvent(window, 'blur'),
        fromEvent(window, 'pagehide'),
        this.router.events.pipe(filter((event) => event instanceof NavigationStart)),
    ).pipe(
        map(() => undefined),
        share(),
    );
}
