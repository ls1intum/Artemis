import { Subject } from 'rxjs';
import { Params } from '@angular/router';

export class MockActivatedRouteWithSubjects {
    private subject = new Subject<Params>();
    params = this.subject;

    setSubject = (subject: Subject<Params>) => {
        this.params = subject;
    };
}
