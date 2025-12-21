import { Observable, Subject } from 'rxjs';
import { NavigationEnd, RouterEvent, RouterState, UrlTree, Router } from '@angular/router';

// Declare vi as global for Vitest environments
declare const vi: (typeof import('vitest'))['vi'] | undefined;

/**
 * Creates a mock function compatible with both Jest and Vitest.
 * Automatically detects which test framework is available and uses
 * the appropriate mock function implementation.
 *
 * @param returnValue - The value the mock should return
 * @returns A mock function that works with jest.fn() or vi.fn()
 */
function createMockFn<T>(returnValue?: T): jest.Mock<any, any> {
    // Check if we're in a Vitest environment
    if (typeof vi !== 'undefined' && vi !== null) {
        return vi.fn().mockReturnValue(returnValue) as unknown as jest.Mock<any, any>;
    }
    // Fall back to Jest
    return jest.fn().mockReturnValue(returnValue);
}

/**
 * Mock router for unit testing Angular components that use Router.
 * Compatible with both Jest and Vitest test frameworks.
 * When using the spies, bear in mind jest.resetAllMocks does not affect them,
 * they need to be reset manually using mockRestore() or mockReset().
 */
export class MockRouter {
    /** Internal URL state */
    _url = '/';

    /** Mock for Router.navigateByUrl() */
    navigateByUrl = createMockFn(Promise.resolve(true));

    /** Mock for Router.navigate() */
    navigate = createMockFn(Promise.resolve(true));

    /** Router state for testing */
    routerState: RouterState;

    /** Mock for Router.createUrlTree() */
    createUrlTree = createMockFn({ path: 'testValue' } as unknown as UrlTree);

    /** Mock for Router.serializeUrl() */
    serializeUrl = createMockFn('testValue');

    /** Subject for emitting router events */
    eventSubject: Subject<RouterEvent> = new Subject<RouterEvent>();

    /** Mock for Router.currentNavigation */
    currentNavigation = createMockFn<ReturnType<Router['currentNavigation']>>(null);

    /**
     * Sets the router state for testing.
     * @param routerState - The router state to set
     */
    setRouterState(routerState: RouterState) {
        this.routerState = routerState;
    }

    /**
     * Observable of router events for testing navigation.
     */
    get events(): Observable<RouterEvent> {
        return this.eventSubject.asObservable();
    }

    /**
     * Sets the current URL and emits a NavigationEnd event.
     * @param url - The URL to set
     */
    setUrl(url: string) {
        this._url = url;
        this.eventSubject.next(new NavigationEnd(0, url, url));
    }

    /**
     * Gets the current URL.
     */
    get url() {
        return this._url;
    }
}
