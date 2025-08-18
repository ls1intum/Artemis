import { Component, ElementRef, computed, effect, inject, input, output } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';

export const enum ImageLoadingStatus {
    SUCCESS = 'success',
    ERROR = 'error',
    LOADING = 'loading',
}

/**
 * Fetches an image from the server and displays it.
 * - Retries once if the initial request fails.
 * - Relies on browser caching (Artemis serves images publicly with proper caching headers, see {@link PublicResourcesConfiguration.java}).
 *
 * Template note:
 * - We used `[attr.src]="localImageUrl()"` → removes the `src` attribute if the value is `undefined`.
 * - We did not use `[src]="localImageUrl()"` → some browsers (e.g. Chrome) convert `undefined` to the string `"undefined"`, causing broken requests.
 */
@Component({
    selector: 'jhi-image',
    template: `
        @if (mobileDragAndDrop()) {
            <img [attr.src]="localImageUrl()" class="dnd-drag-start" draggable="true" [attr.alt]="alt()" cdkDrag />
        } @else {
            <img [attr.src]="localImageUrl()" [attr.alt]="alt()" />
        }
    `,
    imports: [],
})
export class ImageComponent {
    private domSanitizer = inject(DomSanitizer);
    private retried = false;

    element = inject(ElementRef);
    mobileDragAndDrop = input<boolean>(false);
    src = input.required<string>();
    alt = input<string | undefined>(undefined);
    loadingStatus = output<ImageLoadingStatus>();

    private imageResource = httpResource.blob<SafeUrl>(() => this.src(), {
        parse: (blob) => this.domSanitizer.bypassSecurityTrustUrl(URL.createObjectURL(blob)), // we need DomSanitizer to trust the url
    });

    localImageUrl = computed<SafeUrl | undefined>(() => (this.imageResource.hasValue() ? this.imageResource.value() : undefined));

    constructor() {
        effect(() => {
            if (this.imageResource.error() && !this.retried) {
                this.retried = true;
                this.imageResource.reload();
            }
        });
        effect(() => {
            if (this.imageResource.isLoading()) {
                this.loadingStatus.emit(ImageLoadingStatus.LOADING);
            } else if (this.imageResource.error() && this.retried) {
                this.loadingStatus.emit(ImageLoadingStatus.ERROR);
            } else if (this.imageResource.hasValue()) {
                this.loadingStatus.emit(ImageLoadingStatus.SUCCESS);
            }
        });
    }

    retryLoadImage() {
        this.imageResource.reload();
    }
}
