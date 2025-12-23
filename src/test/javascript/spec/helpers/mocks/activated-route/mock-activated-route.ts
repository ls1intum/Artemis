import { ActivatedRoute, Data, Params } from '@angular/router';
import { ReplaySubject } from 'rxjs';

export class MockActivatedRoute extends ActivatedRoute {
    private queryParamsSubject = new ReplaySubject<Params>(1);
    private paramSubject = new ReplaySubject<Params>(1);
    private dataSubject = new ReplaySubject<Data>(1);

    constructor(parameters?: any) {
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
            pagingParams: {
                page: 10,
                ascending: false,
                predicate: 'id',
            },
        });
    }

    get root(): ActivatedRoute {
        return this;
    }

    get firstChild(): ActivatedRoute {
        return this;
    }
    get parent(): ActivatedRoute {
        return this;
    }
}
