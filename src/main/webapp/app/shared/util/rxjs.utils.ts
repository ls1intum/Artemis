import { Observable, Subject } from 'rxjs';

export class SubjectObservablePair<T> {
    subject: Subject<T>;
    observable: Observable<T>;

    constructor() {
        this.subject = new Subject<T>();
        this.observable = this.subject.asObservable();
    }
}
