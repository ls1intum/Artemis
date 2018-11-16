import { Injectable } from '@angular/core';
import { SessionStorageService } from 'ngx-webstorage';

@Injectable({ providedIn: 'root' })
export class StateStorageService {
    constructor(private $sessionStorage: SessionStorageService) {}

    getPreviousState() {
        return this.$sessionStorage.retrieve('previousState');
    }

    resetPreviousState() {
        this.$sessionStorage.clear('previousState');
    }

    storePreviousState(previousStateName: any, previousStateParams: any) {
        const previousState = { name: previousStateName, params: previousStateParams };
        this.$sessionStorage.store('previousState', previousState);
    }

    getDestinationState() {
        return this.$sessionStorage.retrieve('destinationState');
    }

    storeUrl(url: string) {
        this.$sessionStorage.store('previousUrl', url);
    }

    getUrl() {
        return this.$sessionStorage.retrieve('previousUrl');
    }

    storeDestinationState(destinationState: any, destinationStateParams: any, fromState: any) {
        const destinationInfo = {
            destination: {
                name: destinationState.name,
                data: destinationState.data
            },
            params: destinationStateParams,
            from: {
                name: fromState.name
            }
        };
        this.$sessionStorage.store('destinationState', destinationInfo);
    }
}
