import { StorageService } from 'ngx-webstorage/lib/core/interfaces/storageService';
import { Observable } from 'rxjs';

export class MockSyncStorage implements StorageService {
    private static storage: { [key: string]: any } = {};

    clear(key?: string): any {}

    getStrategyName(): string {
        return '';
    }

    observe(key: string): Observable<any> {
        // @ts-ignore
        return undefined;
    }

    retrieve(key: string): any {}

    store(key: string, value: any): any {
        MockSyncStorage.storage[key] = `${value}`;
    }

    static retrieve(key: string): any {
        return this.storage[key];
    }
}
