import { Params } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

export class MockActivatedRoute {
    private innerTestParams?: any;
    private subject: BehaviorSubject<any> = new BehaviorSubject(this.testParams);

    params = this.subject.asObservable();

    constructor(params?: Params) {
        if (params) {
            this.testParams = params;
        } else {
            this.testParams = {};
        }
    }

    get testParams() {
        return this.innerTestParams;
    }

    set testParams(params: {}) {
        this.innerTestParams = params;
        this.subject.next(params);
    }
}
