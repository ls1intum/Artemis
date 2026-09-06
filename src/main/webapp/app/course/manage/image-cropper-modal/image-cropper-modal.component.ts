import { Component, booleanAttribute, input, output, signal } from '@angular/core';
import { ImageCroppedEvent } from 'app/shared-ui/image-cropper/interfaces/image-cropped-event.interface';
import { OutputFormat } from 'app/shared-ui/image-cropper/interfaces/cropper-options.interface';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ImageCropperComponent } from 'app/shared-ui/image-cropper/component/image-cropper.component';
import { TumUiButtonDirective } from '@tumaet/ui-angular';

export interface ImageCropperModalData {
    uploadFile?: File;
    roundCropper?: boolean;
    fileFormat?: OutputFormat;
}

@Component({
    selector: 'jhi-image-cropper-modal',
    templateUrl: './image-cropper-modal.component.html',
    imports: [TranslateDirective, ImageCropperComponent, TumUiButtonDirective],
})
export class ImageCropperModalComponent {
    readonly uploadFile = input<File | undefined>(undefined);
    readonly roundCropper = input(true, { transform: booleanAttribute });
    readonly fileFormat = input<OutputFormat>('png');

    /** Emits the cropped image when the user saves, so the host can upload it. */
    readonly cropped = output<string>();
    /** Emits when the user cancels without saving. */
    readonly cancelled = output<void>();

    readonly croppedImage = signal<string | undefined>(undefined);

    /**
     * Called when an image is cropped.
     * @param event The event containing the cropped image data.
     */
    imageCropped(event: ImageCroppedEvent) {
        this.croppedImage.set(event.base64);
    }

    /**
     * Method is called when the modal is closed by clicking 'Cancel' button.
     */
    onCancel(): void {
        this.cancelled.emit();
    }

    /**
     * Called when the modal is closed by clicking the 'Save' button.
     * The changes are saved and the croppedImage information is transferred.
     */
    onSave(): void {
        const image = this.croppedImage();
        if (image) {
            this.cropped.emit(image);
        }
    }
}
