import { Component, Input, OnChanges, OnInit, OnDestroy, EventEmitter, Output } from '@angular/core';
import { DOMStorageStrategy } from 'ngx-cacheable/common/DOMStorageStrategy';
import { Injectable } from '@angular/core';
import { Cacheable } from 'ngx-cacheable';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, Subject, Subscription, pipe, of, isObservable, UnaryFunction } from 'rxjs';
import { catchError, filter, map, switchMap, tap } from 'rxjs/operators';
import { AccountService } from 'app/core';
import { base64StringToBlob, blobToBase64String } from 'blob-util';

const logoutSubject = new Subject<void>();

@Injectable({ providedIn: 'root' })
export class CacheableImageService implements OnInit, OnDestroy {
    private logoutSubscription: Subscription;

    constructor(private accountService: AccountService, private httpClient: HttpClient) {}

    ngOnInit(): void {
        this.logoutSubscription = this.accountService
            .getAuthenticationState()
            .pipe(
                tap(console.log),
                tap(() => console.log('logout!')),
                tap(() => logoutSubject.next()),
            )
            .subscribe();
    }

    ngOnDestroy(): void {
        if (this.logoutSubscription) {
            this.logoutSubscription.unsubscribe();
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
