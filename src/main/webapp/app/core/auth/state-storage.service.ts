import { Injectable } from '@angular/core';
import { SessionStorageService } from 'ngx-webstorage';

@Injectable({ providedIn: 'root' })
export class StateStorageService {
    constructor(private sessionStorage: SessionStorageService) {}

    /**
     * Get the previous state of the current session.
     */
    getPreviousState(): string {
        return this.sessionStorage.retrieve('previousState');
    }

    /**
     * Reset the previous state of the current session.
     */
    resetPreviousState(): void {
        this.sessionStorage.clear('previousState');
    }

    /**
     * Store a new previous state for the current session.
     * @param previousStateName Name of the new previous state
     * @param previousStateParams Parameters of the new previous state
     */
    storePreviousState(previousStateName: any, previousStateParams: any): void {
        const previousState = { name: previousStateName, params: previousStateParams };
        this.sessionStorage.store('previousState', previousState);
    }

    /**
     * Get the destination state for the current session.
     */
    getDestinationState(): string {
        return this.sessionStorage.retrieve('destinationState');
    }

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

    /**
     * Store a new destination state for the current session.
     * @param destinationState Name of the new destination state
     * @param destinationStateParams Parameters of the new destination state
     * @param fromState the fromState of the new destination state
     */
    storeDestinationState(destinationState: any, destinationStateParams: any, fromState: any): void {
        const destinationInfo = {
            destination: {
                name: destinationState.name,
                data: destinationState.data,
            },
            params: destinationStateParams,
            from: {
                name: fromState.name,
            },
        };
        this.sessionStorage.store('destinationState', destinationInfo);
    }
}
