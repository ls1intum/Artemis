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
        const userAgent = window.navigator.userAgent;
        const platform = window.navigator.platform;
        const navigator = window.navigator as any; // For experimental userAgentData

        // 1. Modern: Navigator.userAgentData (Chromium based)
        if (navigator.userAgentData?.platform) {
            this._isMac.set(navigator.userAgentData.platform === 'macOS');
        }
        // 2. Legacy/Fallback: Navigator.platform & UserAgent
        else {
            const macPlatforms = ['Macintosh', 'MacIntel', 'MacPPC', 'Mac68K'];
            this._isMac.set(macPlatforms.indexOf(platform) !== -1 || /Mac/.test(userAgent));
        }

        // Optional: Detect iOS (often relevant for shortcuts on iPads with keyboards)
        this._isIos.set(/iPhone|iPad|iPod/.test(platform) || (this._isMac() && navigator.maxTouchPoints > 1));
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
