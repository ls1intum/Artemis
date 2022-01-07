import { CropperPosition } from './cropper-position.interface';

export interface ImageCroppedEvent {
    base64?: string;
    width: number;
    height: number;
    cropperPosition: CropperPosition;
    imagePosition: CropperPosition;
    offsetImagePosition?: CropperPosition;
}
