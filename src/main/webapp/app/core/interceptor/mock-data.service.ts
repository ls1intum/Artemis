import { Injectable, isDevMode, signal } from '@angular/core';

const STORAGE_KEY = 'artemis.mockDataEnabled';

@Injectable({ providedIn: 'root' })
export class MockDataService {
    readonly enabled = signal(isDevMode() && localStorage.getItem(STORAGE_KEY) === 'true');

    toggle(): void {
        const next = !this.enabled();
        this.enabled.set(next);
        localStorage.setItem(STORAGE_KEY, String(next));
    }
}
