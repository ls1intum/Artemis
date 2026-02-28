import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

@Injectable({
    providedIn: 'root',
})
export class OsDetectorService {
    private _isMac = signal(false);
    private _isIos = signal(false);
    private platformId = inject(PLATFORM_ID);
    constructor() {
        if (isPlatformBrowser(this.platformId)) {
            this.detectOs();
        }
    }

    private detectOs() {
        const { userAgent } = window.navigator;
        this._isMac.set(/Mac/.test(userAgent));
        this._isIos.set(/iPhone|iPad|iPod/.test(userAgent) || (this._isMac() && window.navigator.maxTouchPoints > 1));
    }

    /** Returns true if the OS is macOS or iOS */
    isMac = computed(() => this._isMac() || this._isIos());

    /** Returns '⌘' for Mac and 'Ctrl' for Windows/Linux */
    actionKeyLabel = computed(() => (this.isMac() ? '⌘' : 'Ctrl'));

    /** Returns true if the event matches the primary action key (Cmd on Mac, Ctrl on Win) */
    isActionKey(event: KeyboardEvent): boolean {
        return this.isMac() ? event.metaKey : event.ctrlKey;
    }
}
