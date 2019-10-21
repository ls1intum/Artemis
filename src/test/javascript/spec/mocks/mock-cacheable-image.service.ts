import { ICacheableImageService } from 'app/shared';
import { Observable, of } from 'rxjs';

export class MockCacheableImageService implements ICacheableImageService {
    loadCachedLocalStorage(url: string): Observable<any> {
        return of();
    }

    loadCachedSessionStorage(url: string): Observable<any> {
        return of();
    }

    loadWithoutCache(url: string): Observable<any> {
        return of();
    }
}
