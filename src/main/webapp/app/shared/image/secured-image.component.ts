import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { BehaviorSubject, isObservable, Observable, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { DomSanitizer } from '@angular/platform-browser';
import { CacheableImageService } from 'app/shared/image/cacheable-image.service';
import { base64StringToBlob } from 'blob-util';

// Status that is emitted to the client to describe the loading status of the picture
export const enum QuizEmitStatus {
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
            <img [attr.src]="dataUrl$ | async" />
        </ng-template>
        <ng-template [ngIf]="this.mobileDragAndDrop">
            <img [attr.src]="dataUrl$ | async" class="dnd-drag-start" draggable="true" dnd-draggable />
        </ng-template>
    `,
})
export class SecuredImageComponent implements OnChanges {
    // This part just creates an rxjs stream from the src
    // this makes sure that we can handle it when the src changes
    // or even when the component gets destroyed
    @Input() mobileDragAndDrop: boolean;
    @Input() private src: string;
    @Input() cachingStrategy = CachingStrategy.LOCAL_STORAGE;
    private src$ = new BehaviorSubject(this.src);
    private retryCounter = 0;

    @Output()
    endLoadingProcess = new EventEmitter<QuizEmitStatus>();

    // this stream will contain the actual url that our img tag will load
    // everytime the src changes, the previous call would be canceled and the
    // new resource would be loaded
    dataUrl$ = this.src$.switchMap(url => this.loadImage(url));

    ngOnChanges(): void {
        this.src$.next(this.src);
    }

    // we need HttpClient to load the image and DomSanitizer to trust the url
    constructor(private domSanitizer: DomSanitizer, private cacheableImageService: CacheableImageService) {}

    // triggers the reload of the picture when the user clicks on a button
    retryLoadImage() {
        this.retryCounter = 0;
        this.endLoadingProcess.emit(QuizEmitStatus.LOADING);
        this.ngOnChanges();
    }

    private loadImage(url: string): Observable<any> {
        return of(null).pipe(
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
            map((base64String: string) => {
                const blob = base64StringToBlob(base64String, 'application/json');
                this.endLoadingProcess.emit(QuizEmitStatus.SUCCESS);
                return this.domSanitizer.bypassSecurityTrustUrl(URL.createObjectURL(blob));
            }),
            catchError(error => {
                if (this.retryCounter === 0) {
                    this.retryCounter++;
                    return this.loadImage(url);
                } else {
                    this.endLoadingProcess.emit(QuizEmitStatus.ERROR);
                }
                return error;
            }),
        );
    }
}
