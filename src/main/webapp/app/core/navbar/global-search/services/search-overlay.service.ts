import { Injectable, signal } from '@angular/core';

@Injectable({
    providedIn: 'root',
})
export class SearchOverlayService {
    // Read/Write signal for the modal state
    isOpen = signal(false);

    open() {
        this.isOpen.set(true);
        // Optional: Lock body scroll
        document.body.style.overflow = 'hidden';
    }

    close() {
        this.isOpen.set(false);
        document.body.style.overflow = '';
    }
    toggle() {
        if (this.isOpen()) {
            this.close();
        } else {
            this.open();
        }
    }
}
