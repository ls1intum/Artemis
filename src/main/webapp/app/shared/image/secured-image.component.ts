import { Component, ElementRef, computed, effect, inject, input, output } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';

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
        @if (mobileDragAndDrop()) {
            <img [attr.src]="localImageUrl() ?? null" class="dnd-drag-start" draggable="true" [attr.alt]="alt() ?? null" cdkDrag />
        } @else {
            <img [attr.src]="localImageUrl() ?? null" [attr.alt]="alt() ?? null" />
        }
    `,
    imports: [],
})
export class SecuredImageComponent {
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
