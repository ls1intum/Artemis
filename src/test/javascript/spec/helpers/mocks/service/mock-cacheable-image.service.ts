import { Observable, of } from 'rxjs';
import { ICacheableImageService } from 'app/shared/image/cacheable-image.service';

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
