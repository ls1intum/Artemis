import { Injectable, inject } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { Observable, filter, fromEvent, map, merge, share } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PageActivityService {
    private readonly router = inject(Router);

    readonly pageLeaving$: Observable<void> = merge(
        fromEvent(document, 'visibilitychange').pipe(filter(() => document.visibilityState === 'hidden')),
        fromEvent(window, 'blur'),
        fromEvent(window, 'pagehide'),
        this.router.events.pipe(filter((event) => event instanceof NavigationStart)),
    ).pipe(
        map(() => undefined),
        share(),
    );
}
