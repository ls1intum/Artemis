import { Injectable, OnDestroy, OnInit } from '@angular/core';
import { DOMStorageStrategy } from 'ngx-cacheable/common/DOMStorageStrategy';
import { Cacheable } from 'ngx-cacheable';
import { HttpClient } from '@angular/common/http';
import { Observable, pipe, Subject, Subscription, UnaryFunction } from 'rxjs';
import { switchMap, tap, distinctUntilChanged } from 'rxjs/operators';
import { AccountService } from 'app/core';
import { blobToBase64String } from 'blob-util';

const logoutSubject = new Subject<void>();

@Injectable({ providedIn: 'root' })
export class CacheableImageService implements OnDestroy {
    private userChangeSubscription: Subscription;

    constructor(private accountService: AccountService, private httpClient: HttpClient) {
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
     * Load the image, use a cache. Cache will only be busted on logout.
     *
     * @param url
     */
    @Cacheable({
        storageStrategy: DOMStorageStrategy,
        cacheBusterObserver: logoutSubject.asObservable(),
        maxCacheCount: 30,
    })
    public loadCached(url: string): Observable<any> {
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
