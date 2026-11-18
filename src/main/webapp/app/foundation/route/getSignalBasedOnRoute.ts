import { Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { distinctUntilChanged, filter, map, startWith } from 'rxjs/operators';

/**
 * Returns a signal that reactively maps the current router URL to a value based on the provided handler.
 */
export function getSignalBasedOnRoute<T>(router: Router, changeHandler: (url: string) => T): Signal<T> {
    return toSignal(
        router.events.pipe(
            filter((event): event is NavigationEnd => event instanceof NavigationEnd),
            map((event) => event.urlAfterRedirects),
            startWith(router.url),
            distinctUntilChanged(),
            map(changeHandler),
        ),
        { requireSync: true },
    );
}
