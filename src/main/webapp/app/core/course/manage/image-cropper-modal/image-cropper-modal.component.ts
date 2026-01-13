import { Component, inject, signal } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ImageCroppedEvent } from 'app/shared/image-cropper/interfaces/image-cropped-event.interface';
import { OutputFormat } from 'app/shared/image-cropper/interfaces/cropper-options.interface';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ImageCropperComponent } from 'app/shared/image-cropper/component/image-cropper.component';

export interface ImageCropperModalData {
    uploadFile?: File;
    roundCropper?: boolean;
    fileFormat?: OutputFormat;
}

@Component({
    selector: 'jhi-image-cropper-modal',
    templateUrl: './image-cropper-modal.component.html',
    imports: [TranslateDirective, ImageCropperComponent],
})
export class ImageCropperModalComponent {
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly config = inject(DynamicDialogConfig<ImageCropperModalData>);

    // State signals
    readonly uploadFile = signal<File | undefined>(this.config.data?.uploadFile);
    readonly croppedImage = signal<string | undefined>(undefined);
    readonly roundCropper = signal(this.config.data?.roundCropper ?? true);
    readonly fileFormat = signal<OutputFormat>(this.config.data?.fileFormat ?? 'png');

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
        this.dialogRef.close();
    }

    /**
     * Called when the modal is closed by clicking the 'Save' button.
     * The changes are saved and the croppedImage information is transferred.
     */
    onSave(): void {
        this.dialogRef.close(this.croppedImage());
    }
}
