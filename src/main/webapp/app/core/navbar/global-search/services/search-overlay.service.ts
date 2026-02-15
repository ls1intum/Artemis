import { Injectable, signal } from '@angular/core';

@Injectable({
    providedIn: 'root',
})
export class SearchOverlayService {
    // Private writable signal for the modal state
    private readonly _isOpen = signal(false);

    // Public readonly view of the signal
    readonly isOpen = this._isOpen.asReadonly();

    open() {
        this._isOpen.set(true);
        document.body.style.overflow = 'hidden';
    }

    close() {
        this._isOpen.set(false);
        document.body.style.overflow = '';
    }

    toggle() {
        if (this._isOpen()) {
            this.close();
        } else {
            this.open();
        }
    }
}
