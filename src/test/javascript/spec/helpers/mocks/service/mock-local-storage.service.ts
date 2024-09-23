import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class MockLocalStorageService {
    storage: { [key: string]: any } = {};
    store = (key: string, value: string) => (this.storage[key] = value);
    retrieve = (key: string) => this.storage[key];
    clear = (key?: string) => {};
}
