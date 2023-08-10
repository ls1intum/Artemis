import { Observable, Subject } from 'rxjs';
import { ActivatedRouteSnapshot, ActivationStart, Event, NavigationEnd, RouterState, UrlTree } from '@angular/router';

// When using the spies, bear in mind jest.resetAllMocks does not affect them, they need to be reset manually
export class MockRouter {
    url = '/';
    navigateByUrl = jest.fn().mockReturnValue(true);
    navigate = jest.fn().mockReturnValue(true);
    routerState: RouterState;
    createUrlTree = jest.fn().mockReturnValue({ path: 'testValue' } as unknown as UrlTree);
    serializeUrl = jest.fn().mockReturnValue('testValue');

    eventSubject: Subject<Event> = new Subject<Event>();

    setRouterState(routerState: RouterState) {
        this.routerState = routerState;
    }

    get events(): Observable<Event> {
        return this.eventSubject.asObservable();
    }

    setUrl(url: string) {
        this.url = url;
        this.eventSubject.next(new NavigationEnd(0, url, url));
    }

    addActivationStart(activatedRouteSnapshot: ActivatedRouteSnapshot) {
        this.eventSubject.next(new ActivationStart(activatedRouteSnapshot));
    }
}
