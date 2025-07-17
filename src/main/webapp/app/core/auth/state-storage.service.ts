import { Injectable, inject } from '@angular/core';
import { SessionStorageService } from 'app/shared/storage/session-storage.service';

// TODO: get rid of this simple wrapper, it does not provide a lot of value
@Injectable({ providedIn: 'root' })
export class StateStorageService {
    private sessionStorage = inject(SessionStorageService);

    /**
     * Store an url as previousURL in the current session.
     * @param url Url to be saved
     */
    storeUrl(url: string) {
        this.sessionStorage.store('previousUrl', url);
    }

    /**
     * Get the previousURL of the current session.
     */
    getUrl(): string | undefined {
        return this.sessionStorage.retrieve<string>('previousUrl');
    }
}
