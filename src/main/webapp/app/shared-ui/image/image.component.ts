import { Component, ElementRef, OnDestroy, computed, effect, inject, input, output } from '@angular/core';
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
 * - Relies on browser caching (Artemis serves images with proper caching headers, see {@link StaticResourcesConfiguration.java}).
 *
 * Template note:
 * - We used `[attr.src]="localImageUrl()"` because it removes the `src` attribute if the value is `undefined`.
 * - We did not use `[src]="localImageUrl()"` because some browsers (e.g. Chrome) convert `undefined` to the string `"undefined"`, causing a request when we do not want
 * to trigger a request.
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
export class ImageComponent implements OnDestroy {
    private domSanitizer = inject(DomSanitizer);
    private retried = false;
    private rawLocalImageUrl?: string;

    element = inject(ElementRef);
    mobileDragAndDrop = input<boolean>(false);
    src = input.required<string>();
    alt = input<string | undefined>(undefined);
    loadingStatus = output<ImageLoadingStatus>();

    private imageResource = httpResource.blob<SafeUrl>(() => this.src(), {
        parse: (blob) => {
            const oldRawLocalImageUrl = this.rawLocalImageUrl;
            if (oldRawLocalImageUrl) {
                URL.revokeObjectURL(oldRawLocalImageUrl);
            }
            this.rawLocalImageUrl = URL.createObjectURL(blob);
            return this.domSanitizer.bypassSecurityTrustUrl(this.rawLocalImageUrl); // we need DomSanitizer to trust the url
        },
    });

    localImageUrl = computed<SafeUrl | undefined>(() => this.getUrlFromResourceIf(this.imageResource.hasValue()));

    constructor() {
        effect(() => {
            const resourceHasError = this.imageResource.error() !== undefined;
            this.retryAfterFirstImageFetchIf(resourceHasError);
        });
        effect(() => {
            const resourceIsLoading = this.imageResource.isLoading();
            const resourceHasError = this.imageResource.error() !== undefined;
            const resourceHasValue = this.imageResource.hasValue();
            this.updateLoadingStatusDependingOnIf(resourceIsLoading, resourceHasError, resourceHasValue);
        });
    }

    ngOnDestroy() {
        if (this.rawLocalImageUrl) {
            URL.revokeObjectURL(this.rawLocalImageUrl);
        }
    }

    private getUrlFromResourceIf(resourceHasValue: boolean): SafeUrl | undefined {
        return resourceHasValue ? this.imageResource.value() : undefined;
    }

    private retryAfterFirstImageFetchIf(resourceHasError: boolean) {
        if (resourceHasError && !this.retried) {
            this.retried = true;
            this.imageResource.reload();
        }
    }

    private updateLoadingStatusDependingOnIf(resourceIsLoading: boolean, resourceHasError: boolean, resourceHasValue: boolean) {
        if (resourceIsLoading) {
            this.loadingStatus.emit(ImageLoadingStatus.LOADING);
        } else if (resourceHasError && this.retried) {
            this.loadingStatus.emit(ImageLoadingStatus.ERROR);
        } else if (resourceHasValue) {
            this.loadingStatus.emit(ImageLoadingStatus.SUCCESS);
        }
    }

    retryLoadImage() {
        this.imageResource.reload();
    }
}
