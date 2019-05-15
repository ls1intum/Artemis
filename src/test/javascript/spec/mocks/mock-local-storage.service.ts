import { StorageService } from 'ngx-webstorage/lib/core/interfaces/storageService';
import { Observable } from 'rxjs';

export class MockLocalStorageService implements StorageService {
    clear(key?: string): any {}

    getStrategyName(): string {
        return '';
    }

    observe(key: string): Observable<any> {
        return undefined;
    }

    retrieve(key: string): any {}

    store(key: string, value: any): any {}
}
