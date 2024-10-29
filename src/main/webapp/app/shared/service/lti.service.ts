import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class LtiService {
    constructor() {}
    private ltiSubject = new BehaviorSubject<boolean>(false);
    isLti$ = this.ltiSubject.asObservable();

    setLti(isLti: boolean) {
        this.ltiSubject.next(isLti);
    }
}
