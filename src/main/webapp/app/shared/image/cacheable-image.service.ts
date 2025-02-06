import { Injectable, OnDestroy, inject } from '@angular/core';
import { Cacheable, LocalStorageStrategy } from 'ts-cacheable';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject, Subscription, UnaryFunction, pipe } from 'rxjs';
import { distinctUntilChanged, switchMap, tap } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { SessionStorageStrategy } from 'app/shared/image/session-storage-strategy';
import { blobToBase64String } from 'app/utils/blob-util';

const logoutSubject = new Subject<void>();

/**
 * Decides if an image (represented as Base64 string) should be stored in the cache.
 * Do not save too big images since space is limited.
 */
const shouldCacheDecider = (result: any) => {
    if (typeof result === 'string') {
        return result.length < 10_000;
    }
    // This should not happen since this service only works with strings.
    return false;
};

export interface ICacheableImageService {
    loadCachedLocalStorage(url: string): Observable<any>;
    loadCachedSessionStorage(url: string): Observable<any>;
    loadWithoutCache(url: string): Observable<any>;
}

@Injectable({ providedIn: 'root' })
export class CacheableImageService implements ICacheableImageService, OnDestroy {
    private accountService = inject(AccountService);
    private httpClient = inject(HttpClient);

    private userChangeSubscription: Subscription;

    constructor() {
        this.init();
    }

    /**
     * Subscribe to the auth service to receive updates about user changes.
     */
    init(): void {
        this.userChangeSubscription = this.accountService
            .getAuthenticationState()
            .pipe(
                // Fires on every event where the user has changed (login/logout).
                distinctUntilChanged(),
                tap(() => logoutSubject.next()),
            )
            .subscribe();
    }

    ngOnDestroy(): void {
        if (this.userChangeSubscription) {
            this.userChangeSubscription.unsubscribe();
        }
    }

    /**
     * Load the image, cache it in the LocalStorage. Cache will only be cleared on logout.
     * Important: Do _not_ use this for large images with more than 150KB because the LocalStorage is only 5-10 MB!
     *
     * @param url
     */
    @Cacheable({
        storageStrategy: LocalStorageStrategy,
        cacheBusterObserver: logoutSubject.asObservable(),
        maxCacheCount: 30,
        shouldCacheDecider,
    })
    public loadCachedLocalStorage(url: string): Observable<any> {
        return this.httpClient.get(url, { responseType: 'blob' }).pipe(this.mapBlobToUrlString());
    }

    /**
     * Load the image, cache them in the SessionStorage. Cache will be cleared on logout or when the browser is closed.
     * Don't overuse this cache, the user could run out of RAM if we store too much in it.
     *
     * @param url
     */
    @Cacheable({
        storageStrategy: SessionStorageStrategy,
        cacheBusterObserver: logoutSubject.asObservable(),
        maxCacheCount: 100,
        shouldCacheDecider,
    })
    public loadCachedSessionStorage(url: string): Observable<any> {
        return this.httpClient.get(url, { responseType: 'blob' }).pipe(this.mapBlobToUrlString());
    }

    /**
     * Load image without using the cache (always triggers the endpoint).
     *
     * @param url
     */
    public loadWithoutCache(url: string): Observable<any> {
        return this.httpClient.get(url, { responseType: 'blob' }).pipe(this.mapBlobToUrlString());
    }

    /**
     * Map a Blob to a storable base64 url string.
     */
    private mapBlobToUrlString = (): UnaryFunction<Observable<Blob>, Observable<string>> => {
        return pipe(switchMap(blobToBase64String));
    };
}
