import { ActivatedRoute, Data, Params, Router, RouterEvent } from '@angular/router';
import { Observable, ReplaySubject } from 'rxjs';
import { SpyObject } from '../../spyobject';
import { SinonStub } from 'sinon';

export class MockActivatedRoute extends ActivatedRoute {
    private queryParamsSubject = new ReplaySubject<Params>();
    private paramSubject = new ReplaySubject<Params>();
    private dataSubject = new ReplaySubject<Data>();

    constructor(parameters: Params) {
        super();
        this.queryParams = this.queryParamsSubject.asObservable();
        this.params = this.paramSubject.asObservable();
        this.data = this.dataSubject.asObservable();
        this.setParameters(parameters);
    }

    setParameters(parameters: Params): void {
        this.queryParamsSubject.next(parameters);
        this.paramSubject.next(parameters);
        this.dataSubject.next({
            ...parameters,
            defaultSort: 'id,desc',
        });
    }
}

export class MockRouter extends SpyObject {
    navigateSpy: SinonStub;
    navigateByUrlSpy: SinonStub;
    events?: Observable<RouterEvent>;
    routerState: any;
    url = '';

    constructor() {
        super(Router);
        this.navigateSpy = this.spy('navigate');
        this.navigateByUrlSpy = this.spy('navigateByUrl');
    }

    setEvents(events: Observable<RouterEvent>): void {
        this.events = events;
    }

    setRouterState(routerState: any): void {
        this.routerState = routerState;
    }
}
