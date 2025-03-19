import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class LtiService {
    private shownViaLtiSubject = new BehaviorSubject<boolean>(false);
    isShownViaLti$ = this.shownViaLtiSubject.asObservable();

    private multiLaunchSubject = new BehaviorSubject<boolean>(false);
    isMultiLaunch$ = this.multiLaunchSubject.asObservable();

    setShownViaLti(shownViaLti: boolean) {
        this.shownViaLtiSubject.next(shownViaLti);
    }

    setMultiLaunch(isMultiLaunch: boolean) {
        this.multiLaunchSubject.next(isMultiLaunch);
    }
}
