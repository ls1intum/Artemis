import { Component, Input, OnChanges, EventEmitter, Output } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { DomSanitizer } from '@angular/platform-browser';

// Status that is emitted to the client to describe the loading status of the picture
export const enum QuizEmitStatus {
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
        <ng-template [ngIf]="this.dragAndDrop === undefined || this.dragAndDrop !== true">
            <img [attr.src]="dataUrl$ | async" />
        </ng-template>
        <ng-template [ngIf]="this.dragAndDrop === true">
            <img [attr.src]="dataUrl$ | async" class="dnd-drag-start" draggable="true" dnd-draggable />
        </ng-template>
    `,
})
export class SecuredImageComponent implements OnChanges {
    // This part just creates an rxjs stream from the src
    // this makes sure that we can handle it when the src changes
    // or even when the component gets destroyed
    @Input()
    dragAndDrop: boolean;
    @Input()
    private src: string;
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
    constructor(private httpClient: HttpClient, private domSanitizer: DomSanitizer) {}

    // triggers the reload of the picture when the user clicks on a button
    retryLoadImage() {
        this.retryCounter = 0;
        this.endLoadingProcess.emit(QuizEmitStatus.LOADING);
        this.ngOnChanges();
    }

    private loadImage(url: string): Observable<any> {
        return this.httpClient
            .get(url, { responseType: 'blob' })
            .map(e => {
                this.endLoadingProcess.emit(QuizEmitStatus.SUCCESS);
                return this.domSanitizer.bypassSecurityTrustUrl(URL.createObjectURL(e));
            })
            .catch(error => {
                if (this.retryCounter === 0) {
                    this.retryCounter++;
                    return this.loadImage(url);
                } else {
                    this.endLoadingProcess.emit(QuizEmitStatus.ERROR);
                }
                return error;
            });
    }
}
