import { Component, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ImageCroppedEvent } from 'app/shared/image-cropper/interfaces/image-cropped-event.interface';
import { OutputFormat } from 'app/shared/image-cropper/interfaces/cropper-options.interface';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ImageCropperComponent } from 'app/shared/image-cropper/component/image-cropper.component';

@Component({
    selector: 'jhi-image-cropper-modal',
    templateUrl: './image-cropper-modal.component.html',
    imports: [TranslateDirective, ImageCropperComponent],
})
export class ImageCropperModalComponent {
    private activeModal = inject(NgbActiveModal);

    uploadFile?: File;
    croppedImage?: string;
    roundCropper = true;
    fileFormat: OutputFormat = 'png';

    /**
     * Called when an image is cropped.
     * @param event The event containing the cropped image data.
     */
    imageCropped(event: ImageCroppedEvent) {
        this.croppedImage = event.base64;
    }

    /**
     * Method is called when the modal is closed by clicking 'Cancel' button.
     */
    onCancel(): void {
        this.activeModal.close();
    }

    /**
     * Called when the modal is closed by clicking the 'Save' button.
     * The changes are saved and the croppedImage information is transferred.
     */
    onSave(): void {
        this.activeModal.close(this.croppedImage);
    }
}
