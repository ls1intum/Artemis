import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class SidebarEventService {
    private sidebarCardClickedEvent = new BehaviorSubject<string | number | null>(null);

    emitSidebarCardEvent(itemId: string | number) {
        this.sidebarCardClickedEvent.next(itemId);
    }

    emitResetValue() {
        this.sidebarCardClickedEvent.next(null);
    }

    sidebarCardEventListener() {
        return this.sidebarCardClickedEvent.asObservable();
    }
}
