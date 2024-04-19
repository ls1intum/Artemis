import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ImageCroppedEvent } from 'app/shared/image-cropper/interfaces/image-cropped-event.interface';

@Component({
    selector: 'jhi-image-cropper-modal',
    templateUrl: './image-cropper-modal.component.html',
})
export class ImageCropperModalComponent {
    courseImageUploadFile?: File;
    croppedImage?: string;

    constructor(private activeModal: NgbActiveModal) {}

    /**
     * @param event
     */
    imageCropped(event: ImageCroppedEvent) {
        this.croppedImage = event.base64;
    }

    /**
     * Method is called when the process is canceled.
     */
    onCancel(): void {
        this.activeModal.close();
    }

    /**
     * Method is called when the process is saved.
     */
    onSave(): void {
        this.activeModal.close(this.croppedImage);
    }
}
