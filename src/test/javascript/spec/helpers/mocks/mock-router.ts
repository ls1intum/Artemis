import { EMPTY, Observable } from 'rxjs';
import { RouterEvent, RouterState } from '@angular/router';

// When using the spies, bear in mind jest.resetAllMocks does not affect them, they need to be reset manually
export class MockRouter {
    url = '/';
    setUrl = (url: string) => (this.url = url);
    navigateByUrl = jest.fn().mockReturnValue(true);
    navigate = jest.fn().mockReturnValue(true);
    events: Observable<RouterEvent> = EMPTY;
    routerState: RouterState;
    setRouterState(routerState: RouterState) {
        this.routerState = routerState;
    }
}
