import { Params } from '@angular/router';
import { Subject } from 'rxjs';

export class MockActivatedRouteWithSubjects {
    private subject = new Subject<Params>();
    params = this.subject;

    setSubject = (subject: Subject<Params>) => {
        this.params = subject;
    };
}
