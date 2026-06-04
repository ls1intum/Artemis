import { Injectable, inject } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { Observable, merge } from 'rxjs';
import { filter, mapTo } from 'rxjs/operators';

export type PageLeaveReason = 'visibility-change' | 'page-hide' | 'route-change';

@Injectable({
    providedIn: 'root',
})
export class PageActivityService {
    private readonly router = inject(Router);
    readonly pageLeaving$: Observable<void>;

    constructor() {
        this.pageLeaving$ = merge(this.visibilityChange$(), this.pageHide$(), this.routeChange$());
    }

    private visibilityChange$(): Observable<void> {
        return new Observable((observer) => {
            const handler = () => {
                if (document.visibilityState === 'hidden') {
                    observer.next();
                }
            };

            document.addEventListener('visibilitychange', handler);

            return () => {
                document.removeEventListener('visibilitychange', handler);
            };
        });
    }

    private pageHide$(): Observable<void> {
        return new Observable((observer) => {
            const handler = () => {
                observer.next();
            };

            window.addEventListener('pagehide', handler);

            return () => {
                window.removeEventListener('pagehide', handler);
            };
        });
    }

    private routeChange$(): Observable<void> {
        return this.router.events.pipe(
            filter((event) => event instanceof NavigationStart),
            mapTo(undefined),
        );
    }
}
