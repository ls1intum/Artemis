import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Subject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getSignalBasedOnRoute } from './getSignalBasedOnRoute';

describe('getSignalBasedOnRouteChange', () => {
    setupTestBed({ zoneless: true });

    let routerEvents: Subject<NavigationEnd>;
    let router: Router;

    beforeEach(() => {
        routerEvents = new Subject<NavigationEnd>();
        router = {
            url: '/initial',
            events: routerEvents.asObservable(),
        } as Router;

        TestBed.configureTestingModule({});
    });

    it('should initialize the signal with the current router URL', () => {
        const routeChangeSignal = TestBed.runInInjectionContext(() => getSignalBasedOnRoute(router, (url) => url));

        expect(routeChangeSignal()).toBe('/initial');
    });

    it('should update the signal after a navigation ends', () => {
        const routeChangeSignal = TestBed.runInInjectionContext(() => getSignalBasedOnRoute(router, (url) => url));

        routerEvents.next(new NavigationEnd(1, '/next', '/next-after-redirect'));

        expect(routeChangeSignal()).toBe('/next-after-redirect');
    });

    it('should map the URL through the change handler', () => {
        const routeChangeSignal = TestBed.runInInjectionContext(() => getSignalBasedOnRoute(router, (url) => url.includes('course-management')));

        expect(routeChangeSignal()).toBe(false);

        routerEvents.next(new NavigationEnd(1, '/course-management/1', '/course-management/1'));

        expect(routeChangeSignal()).toBe(true);
    });

    it('should not call the change handler for unchanged NavigationEnd URLs', () => {
        const changeHandler = vi.fn((url: string) => url);
        TestBed.runInInjectionContext(() => getSignalBasedOnRoute(router, changeHandler));

        routerEvents.next(new NavigationEnd(1, '/same', '/same'));
        routerEvents.next(new NavigationEnd(2, '/same', '/same'));

        expect(changeHandler).toHaveBeenCalledTimes(2);
    });
});
