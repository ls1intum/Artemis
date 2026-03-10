import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ConversationSelectionState {
    openPostId = signal<number | undefined>(undefined);

    setOpenPostId(id: number | undefined) {
        this.openPostId.set(id);
    }
}
