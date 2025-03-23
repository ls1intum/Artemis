import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class LtiService {
    private shownViaLtiSubject = new BehaviorSubject<boolean>(false);
    isShownViaLti$ = this.shownViaLtiSubject.asObservable();

    private groupedUnitViewSubject = new BehaviorSubject<boolean>(false);
    isGroupedUnitView$ = this.shownViaLtiSubject.asObservable();

    setShownViaLti(shownViaLti: boolean) {
        this.shownViaLtiSubject.next(shownViaLti);
    }

    setGroupedUnitView(groupedUnitView: boolean) {
        this.groupedUnitViewSubject.next(groupedUnitView);
    }
}
