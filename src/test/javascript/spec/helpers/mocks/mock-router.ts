import { Observable, Subject } from 'rxjs';
import { NavigationEnd, RouterEvent, RouterState, UrlTree, Router } from '@angular/router';

// When using the spies, bear in mind jest.resetAllMocks does not affect them, they need to be reset manually
export class MockRouter {
    _url = '/';
    navigateByUrl = jest.fn().mockReturnValue(Promise.resolve(true));
    navigate = jest.fn().mockReturnValue(Promise.resolve(true));
    routerState: RouterState;
    createUrlTree = jest.fn().mockReturnValue({ path: 'testValue' } as unknown as UrlTree);
    serializeUrl = jest.fn().mockReturnValue('testValue');

    eventSubject: Subject<RouterEvent> = new Subject<RouterEvent>();

    currentNavigation = jest.fn<ReturnType<Router['currentNavigation']>, []>().mockReturnValue(null);

    setRouterState(routerState: RouterState) {
        this.routerState = routerState;
    }

    get events(): Observable<RouterEvent> {
        return this.eventSubject.asObservable();
    }

    setUrl(url: string) {
        this._url = url;
        this.eventSubject.next(new NavigationEnd(0, url, url));
    }
    get url() {
        return this._url;
    }
}
