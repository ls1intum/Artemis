import { ElementRef, Injectable } from '@angular/core';
import { CropperSettings } from '../interfaces/cropper.settings';
import { resizeCanvas } from '../utils/resize.utils';
import { LoadedImage } from 'app/shared/image-cropper/interfaces/loaded-image.interface';
import { CropperPosition } from 'app/shared/image-cropper/interfaces/cropper-position.interface';
import { ImageCroppedEvent } from 'app/shared/image-cropper/interfaces/image-cropped-event.interface';

@Injectable({ providedIn: 'root' })
export class CropService {
    crop(sourceImage: ElementRef, loadedImage: LoadedImage, cropper: CropperPosition, settings: CropperSettings): ImageCroppedEvent | undefined {
        const imagePosition = CropService.getImagePosition(sourceImage, loadedImage, cropper, settings);
        const width = imagePosition.x2 - imagePosition.x1;
        const height = imagePosition.y2 - imagePosition.y1;
        const cropCanvas = document.createElement('canvas') as HTMLCanvasElement;
        cropCanvas.width = width;
        cropCanvas.height = height;

        const ctx = cropCanvas.getContext('2d');
        if (!ctx) {
            return;
        }
        if (settings.backgroundColor != undefined) {
            ctx.fillStyle = settings.backgroundColor;
            ctx.fillRect(0, 0, width, height);
        }

        const scaleX = (settings.transform.scale || 1) * (settings.transform.flipH ? -1 : 1);
        const scaleY = (settings.transform.scale || 1) * (settings.transform.flipV ? -1 : 1);

        const transformedImage = loadedImage.transformed!;
        ctx.setTransform(scaleX, 0, 0, scaleY, transformedImage.size.width / 2, transformedImage.size.height / 2);
        ctx.translate(-imagePosition.x1 / scaleX, -imagePosition.y1 / scaleY);
        ctx.rotate(((settings.transform.rotate || 0) * Math.PI) / 180);
        ctx.drawImage(transformedImage.image, -transformedImage.size.width / 2, -transformedImage.size.height / 2);

        const output: ImageCroppedEvent = {
            width,
            height,
            imagePosition,
            cropperPosition: { ...cropper },
        };
        if (settings.containWithinAspectRatio) {
            output.offsetImagePosition = CropService.getOffsetImagePosition(sourceImage, loadedImage, cropper, settings);
        }
        const resizeRatio = this.getResizeRatio(width, height, settings);
        if (resizeRatio !== 1) {
            output.width = Math.round(width * resizeRatio);
            output.height = settings.maintainAspectRatio ? Math.round(output.width / settings.aspectRatio) : Math.round(height * resizeRatio);
            resizeCanvas(cropCanvas, output.width, output.height);
        }
        output.base64 = cropCanvas.toDataURL('image/' + settings.format, this.getQuality(settings));
        return output;
    }

    private static getImagePosition(sourceImage: ElementRef, loadedImage: LoadedImage, cropper: CropperPosition, settings: CropperSettings): CropperPosition {
        const sourceImageElement = sourceImage.nativeElement;
        const ratio = loadedImage.transformed!.size.width / sourceImageElement.offsetWidth;

        const out: CropperPosition = {
            x1: Math.round(cropper.x1 * ratio),
            y1: Math.round(cropper.y1 * ratio),
            x2: Math.round(cropper.x2 * ratio),
            y2: Math.round(cropper.y2 * ratio),
        };

        if (!settings.containWithinAspectRatio) {
            out.x1 = Math.max(out.x1, 0);
            out.y1 = Math.max(out.y1, 0);
            out.x2 = Math.min(out.x2, loadedImage.transformed!.size.width);
            out.y2 = Math.min(out.y2, loadedImage.transformed!.size.height);
        }

        return out;
    }

    private static getOffsetImagePosition(sourceImage: ElementRef, loadedImage: LoadedImage, cropper: CropperPosition, settings: CropperSettings): CropperPosition {
        const canvasRotation = settings.canvasRotation + loadedImage.exifTransform.rotate;
        const sourceImageElement = sourceImage.nativeElement;
        const ratio = loadedImage.transformed!.size.width / sourceImageElement.offsetWidth;
        let offsetX: number;
        let offsetY: number;

        if (canvasRotation % 2) {
            offsetX = (loadedImage.transformed!.size.width - loadedImage.original.size.height) / 2;
            offsetY = (loadedImage.transformed!.size.height - loadedImage.original.size.width) / 2;
        } else {
            offsetX = (loadedImage.transformed!.size.width - loadedImage.original.size.width) / 2;
            offsetY = (loadedImage.transformed!.size.height - loadedImage.original.size.height) / 2;
        }

        const out: CropperPosition = {
            x1: Math.round(cropper.x1 * ratio) - offsetX,
            y1: Math.round(cropper.y1 * ratio) - offsetY,
            x2: Math.round(cropper.x2 * ratio) - offsetX,
            y2: Math.round(cropper.y2 * ratio) - offsetY,
        };

        if (!settings.containWithinAspectRatio) {
            out.x1 = Math.max(out.x1, 0);
            out.y1 = Math.max(out.y1, 0);
            out.x2 = Math.min(out.x2, loadedImage.transformed!.size.width);
            out.y2 = Math.min(out.y2, loadedImage.transformed!.size.height);
        }

        return out;
    }

    getResizeRatio(width: number, height: number, settings: CropperSettings): number {
        const ratioWidth = settings.resizeToWidth / width;
        const ratioHeight = settings.resizeToHeight / height;
        const ratios = new Array<number>();

        if (settings.resizeToWidth > 0) {
            ratios.push(ratioWidth);
        }
        if (settings.resizeToHeight > 0) {
            ratios.push(ratioHeight);
        }

        const result = ratios.length === 0 ? 1 : Math.min(...ratios);

        if (result > 1 && !settings.onlyScaleDown) {
            return result;
        }
        return Math.min(result, 1);
    }

    getQuality(settings: CropperSettings): number {
        return Math.min(1, Math.max(0, settings.imageQuality / 100));
    }
}
