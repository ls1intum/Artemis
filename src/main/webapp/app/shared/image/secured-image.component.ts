import { Component, ElementRef, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { BehaviorSubject, isObservable, Observable, of } from 'rxjs';
import { catchError, filter, map, switchMap, tap } from 'rxjs/operators';
import { DomSanitizer } from '@angular/platform-browser';
import { CacheableImageService } from 'app/shared/image/cacheable-image.service';
import { base64StringToBlob } from 'app/utils/blob-util';

// Status that is emitted to the client to describe the loading status of the picture
export const enum ImageLoadingStatus {
    SUCCESS = 'success',
    ERROR = 'error',
    LOADING = 'loading',
}

export enum CachingStrategy {
    LOCAL_STORAGE = 'LOCAL_STORAGE',
    SESSION_STORAGE = 'SESSION_STORAGE',
    NONE = 'NONE',
}

/**
 * Solution taken from: https://stackblitz.com/edit/secure-image-loads?file=app%2Fsecured-image.component.ts
 * Some browsers (i.e. Chrome) perform some toString function on the src attribute, which causes null to become 'null'
 * instead of '', thus triggering to the browser to look for //domain.com/null which results in an error
 * That's why I had to replace the attribute [src] with [attr.src]
 * This works because instead of setting src to either 'null' or '', the src attribute isn't set at all as long as the
 * variable/path used is not set/resolved, therefore not triggering the error.
 */
@Component({
    selector: 'jhi-secured-image',
    template: `
        <ng-template [ngIf]="!this.mobileDragAndDrop">
            <img [attr.src]="dataUrl | async" alt="alt" />
        </ng-template>
        <ng-template [ngIf]="this.mobileDragAndDrop">
            <img [attr.src]="dataUrl | async" class="dnd-drag-start" draggable="true" alt="alt" cdkDrag />
        </ng-template>
    `,
})
export class SecuredImageComponent implements OnChanges, OnInit {
    // This part just creates an rxjs stream from the src
    // this makes sure that we can handle it when the src changes
    // or even when the component gets destroyed
    @Input() mobileDragAndDrop = false;
    @Input() src: string;
    @Input() cachingStrategy = CachingStrategy.SESSION_STORAGE;
    @Input() alt = '';
    private srcSubject?: BehaviorSubject<string>;
    dataUrl: Observable<string>;
    private retryCounter = 0;

    @Output()
    endLoadingProcess = new EventEmitter<ImageLoadingStatus>();

    ngOnInit(): void {
        this.srcSubject = new BehaviorSubject(this.src);
        // this stream will contain the actual url that our img tag will load
        // everytime the src changes, the previous call would be canceled and the
        // new resource would be loaded
        this.dataUrl = this.srcSubject.pipe(
            filter((url) => !!url),
            switchMap((url) => this.loadImage(url)),
        );
    }

    ngOnChanges(): void {
        if (this.srcSubject) {
            this.srcSubject.next(this.src);
        }
    }

    // we need HttpClient to load the image and DomSanitizer to trust the url
    constructor(private domSanitizer: DomSanitizer, private cacheableImageService: CacheableImageService, public element: ElementRef) {}

    // triggers the reload of the picture when the user clicks on a button
    retryLoadImage() {
        this.retryCounter = 0;
        this.endLoadingProcess.emit(ImageLoadingStatus.LOADING);
        this.ngOnChanges();
    }

    /**
     * Load the image and decide by the active cache strategy if a cache should be used.
     * The requested image will be declared as safe by this method so that angular will not complain.
     * This method has a retry mechanism and will try a couple of times to retry downloading the file if it fails.
     *
     * @param url of the image on the server.
     */
    private loadImage(url: string): Observable<any> {
        return of(undefined).pipe(
            // Load the image from the server with the active caching strategy.
            switchMap(() => {
                let res;
                if (this.cachingStrategy === CachingStrategy.SESSION_STORAGE) {
                    res = this.cacheableImageService.loadCachedSessionStorage(url);
                } else if (this.cachingStrategy === CachingStrategy.LOCAL_STORAGE) {
                    res = this.cacheableImageService.loadCachedLocalStorage(url);
                } else {
                    res = this.cacheableImageService.loadWithoutCache(url);
                }
                // If the result is cached, it will not be an observable but a normal object - in this case it needs to be wrapped into an observable.
                return isObservable(res) ? res : of(res);
            }),
            // The image will be loaded as a base64 string, so it needs to be converted to a blob before it can be used.
            map((base64String: string) => {
                return base64StringToBlob(base64String, 'application/json');
            }),
            // We need to declare the blob as safe, otherwise angular will complain about the inserted element.
            map((blob: Blob) => {
                return this.domSanitizer.bypassSecurityTrustUrl(URL.createObjectURL(blob));
            }),
            // Emit that the file was loaded successfully.
            tap(() => {
                this.endLoadingProcess.emit(ImageLoadingStatus.SUCCESS);
            }),
            catchError((error) => {
                if (this.retryCounter === 0) {
                    this.retryCounter++;
                    return this.loadImage(url);
                } else {
                    this.endLoadingProcess.emit(ImageLoadingStatus.ERROR);
                }
                return error;
            }),
        );
    }
}
