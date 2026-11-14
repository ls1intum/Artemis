import { Subject } from 'rxjs';
import { convertToParamMap, Params } from '@angular/router';

export class MockActivatedRouteWithSubjects {
    private subject = new Subject<Params>();
    params = this.subject;
    snapshot = { paramMap: convertToParamMap({ courseId: '1' }), data: {} };
    setSubject = (subject: Subject<Params>) => {
        this.params = subject;
    };
}
