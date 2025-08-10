import { AsyncPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, ElementRef, EventEmitter, Input, OnChanges, OnInit, Output, inject } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, filter, map, switchMap, tap } from 'rxjs/operators';

// Status that is emitted to the client to describe the loading status of the picture
export const enum ImageLoadingStatus {
    SUCCESS = 'success',
    ERROR = 'error',
    LOADING = 'loading',
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
        @if (!this.mobileDragAndDrop) {
            <img [attr.src]="dataUrl | async" alt="alt" />
        }
        @if (this.mobileDragAndDrop) {
            <img [attr.src]="dataUrl | async" class="dnd-drag-start" draggable="true" alt="alt" cdkDrag />
        }
    `,
    imports: [AsyncPipe],
})
export class SecuredImageComponent implements OnChanges, OnInit {
    private domSanitizer = inject(DomSanitizer);
    private http = inject(HttpClient);
    element = inject(ElementRef);

    // This part just creates an rxjs stream from the src
    // this makes sure that we can handle it when the src changes
    // or even when the component gets destroyed
    @Input() mobileDragAndDrop = false;
    @Input() src: string;
    @Input() alt = '';
    private srcSubject?: BehaviorSubject<string>;
    dataUrl: Observable<string>;
    private retryCounter = 0;

    @Output() endLoadingProcess = new EventEmitter<ImageLoadingStatus>();

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

    ngOnChanges() {
        if (this.srcSubject) {
            this.srcSubject.next(this.src);
        }
    }

    // we need HttpClient to load the image and DomSanitizer to trust the url

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
            // Load the image from the server, let the browser cache it.
            switchMap(() => {
                // TODO: implement properly with browser's caching strategy
                return this.http.get(url, { responseType: 'blob', withCredentials: true });
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
                throw error;
            }),
        );
    }
}
