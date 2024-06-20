import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class SidebarEventService {
    private sidebarCardClickedEvent = new BehaviorSubject<string | number | null>(null);
    private sidebarAccordionPlusClickedEvent = new BehaviorSubject<string | null>(null);

    emitSidebarCardEvent(itemId: string | number) {
        this.sidebarCardClickedEvent.next(itemId);
    }

    emitResetValue() {
        this.sidebarCardClickedEvent.next(null);
    }

    sidebarCardEventListener() {
        return this.sidebarCardClickedEvent.asObservable();
    }

    emitSidebarAccordionPlusClickedEvent(groupkey: string) {
        this.sidebarAccordionPlusClickedEvent.next(groupkey);
        this.sidebarAccordionPlusClickedEvent.next(null);
    }

    sidebarAccordionPlusClickedEventListener() {
        return this.sidebarAccordionPlusClickedEvent.asObservable();
    }
}
