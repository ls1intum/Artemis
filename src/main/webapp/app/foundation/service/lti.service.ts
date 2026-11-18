import { Service } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Service()
export class LtiService {
    private shownViaLtiSubject = new BehaviorSubject<boolean>(false);
    isShownViaLti$ = this.shownViaLtiSubject.asObservable();

    private isMultiLaunch = new BehaviorSubject<boolean>(false);
    isMultiLaunch$ = this.isMultiLaunch.asObservable();

    setShownViaLti(shownViaLti: boolean) {
        this.shownViaLtiSubject.next(shownViaLti);
    }

    setMultiLaunch(isMultiLaunch: boolean) {
        this.isMultiLaunch.next(isMultiLaunch);
    }
}
