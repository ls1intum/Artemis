import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

export class MockActivatedRoute extends ActivatedRoute {
    testParams: { submissionId: string; exerciseId: number };
    constructor(parameters?: any) {
        super();
        this.queryParams = of(parameters);
        this.params = of(parameters);
        this.data = of({
            ...parameters,
            pagingParams: {
                page: 10,
                ascending: false,
                predicate: 'id',
            },
        });
    }
}
