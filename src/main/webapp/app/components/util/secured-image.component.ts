import { Component, Input, OnChanges } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { DomSanitizer } from '@angular/platform-browser';

/**
 * Solution taken from: https://stackblitz.com/edit/secure-image-loads?file=app%2Fsecured-image.component.ts
 */
@Component({
    selector: 'jhi-secured-image',
    template: `<img [src]="dataUrl$|async"/>`
})
export class SecuredImageComponent implements OnChanges  {
    // This part just creates an rxjs stream from the src
    // this makes sure that we can handle it when the src changes
    // or even when the component gets destroyed
    @Input() private src: string;
    private src$ = new BehaviorSubject(this.src);

    // this stream will contain the actual url that our img tag will load
    // everytime the src changes, the previous call would be canceled and the
    // new resource would be loaded
    dataUrl$ = this.src$.switchMap(url => this.loadImage(url));

    ngOnChanges(): void {
        this.src$.next(this.src);
    }

    // we need HttpClient to load the image and DomSanitizer to trust the url
    constructor(private httpClient: HttpClient, private domSanitizer: DomSanitizer) {
    }

    private loadImage(url: string): Observable<any> {
        return this.httpClient
            .get(url, {responseType: 'blob'})
            .map(e => this.domSanitizer.bypassSecurityTrustUrl(URL.createObjectURL(e)));
    }
}
