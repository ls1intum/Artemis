import { Service, signal } from '@angular/core';

@Service()
export class ConversationSelectionState {
    openPostId = signal<number | undefined>(undefined);

    setOpenPostId(id: number | undefined) {
        this.openPostId.set(id);
    }
}
