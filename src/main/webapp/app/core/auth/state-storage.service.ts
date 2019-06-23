import { Injectable } from '@angular/core';
import { SessionStorageService } from 'ngx-webstorage';

@Injectable({ providedIn: 'root' })
export class StateStorageService {
    constructor(private $sessionStorage: SessionStorageService) {}

    getPreviousState(): string | null {
        return this.$sessionStorage.retrieve('previousState');
    }

    resetPreviousState(): void {
        this.$sessionStorage.clear('previousState');
    }

    storePreviousState(previousStateName: any, previousStateParams: any): void {
        const previousState = { name: previousStateName, params: previousStateParams };
        this.$sessionStorage.store('previousState', previousState);
    }

    getDestinationState(): string | null {
        return this.$sessionStorage.retrieve('destinationState');
    }

    storeUrl(url: string | null) {
        this.$sessionStorage.store('previousUrl', url);
    }

    getUrl(): string | null {
        return this.$sessionStorage.retrieve('previousUrl');
    }

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
        this.$sessionStorage.store('destinationState', destinationInfo);
    }
}
