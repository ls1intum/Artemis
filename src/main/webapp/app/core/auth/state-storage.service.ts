import { Injectable, inject } from '@angular/core';
import { SessionStorageService } from 'ngx-webstorage';

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
    getUrl(): string {
        return this.sessionStorage.retrieve('previousUrl');
    }
}
