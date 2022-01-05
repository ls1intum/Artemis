import { Injectable } from '@angular/core';
import { CropperSettings } from '../interfaces/cropper.settings';
import { ExifTransform } from '../interfaces/exif-transform.interface';
import { getTransformationsFromExifData, supportsAutomaticRotation } from '../utils/exif.utils';
import { LoadedImage } from 'app/shared/image-cropper/interfaces/loaded-image.interface';
import { Dimensions } from 'app/shared/image-cropper/interfaces/dimensions.interface';

interface LoadImageBase64 {
    originalImage: HTMLImageElement;
    originalBase64: string;
}

@Injectable({ providedIn: 'root' })
export class LoadImageService {
    private autoRotateSupported: Promise<boolean> = supportsAutomaticRotation();

    loadImageFile(file: File, cropperSettings: CropperSettings): Promise<LoadedImage> {
        return new Promise((resolve, reject) => {
            const fileReader = new FileReader();
            fileReader.onload = (event: any) => {
                this.loadImage(event.target.result, file.type, cropperSettings).then(resolve).catch(reject);
            };
            fileReader.readAsDataURL(file);
        });
    }

    private loadImage(imageBase64: string, imageType: string, cropperSettings: CropperSettings): Promise<LoadedImage> {
        if (!LoadImageService.isValidImageType(imageType)) {
            return Promise.reject(new Error('Invalid image type'));
        }
        return this.loadBase64Image(imageBase64, cropperSettings);
    }

    private static isValidImageType(type: string): boolean {
        return /image\/(png|jpg|jpeg|bmp|gif|tiff|webp)/.test(type);
    }

    loadImageFromURL(url: string, cropperSettings: CropperSettings): Promise<LoadedImage> {
        return new Promise((resolve, reject) => {
            const img = new Image();
            img.onerror = () => reject;
            img.onload = () => {
                const canvas = document.createElement('canvas');
                const context = canvas.getContext('2d')!;
                canvas.width = img.width;
                canvas.height = img.height;
                context.drawImage(img, 0, 0);
                this.loadBase64Image(canvas.toDataURL(), cropperSettings).then(resolve);
            };
            img.crossOrigin = 'anonymous';
            img.src = url;
        });
    }

    loadBase64Image(imageBase64: string, cropperSettings: CropperSettings): Promise<LoadedImage> {
        return new Promise<LoadImageBase64>((resolve, reject) => {
            const originalImage = new Image();
            originalImage.onload = () =>
                resolve({
                    originalImage,
                    originalBase64: imageBase64,
                });
            originalImage.onerror = reject;
            originalImage.src = imageBase64;
        }).then((res: LoadImageBase64) => this.transformImageBase64(res, cropperSettings));
    }

    private async transformImageBase64(res: LoadImageBase64, cropperSettings: CropperSettings): Promise<LoadedImage> {
        const autoRotate = await this.autoRotateSupported;
        const exifTransform = await getTransformationsFromExifData(autoRotate ? -1 : res.originalBase64);
        if (!res.originalImage || !res.originalImage.complete) {
            return Promise.reject(new Error('No image loaded'));
        }
        const loadedImage = {
            original: {
                base64: res.originalBase64,
                image: res.originalImage,
                size: {
                    width: res.originalImage.naturalWidth,
                    height: res.originalImage.naturalHeight,
                },
            },
            exifTransform,
        };
        return this.transformLoadedImage(loadedImage, cropperSettings);
    }

    async transformLoadedImage(loadedImage: LoadedImage, cropperSettings: CropperSettings): Promise<LoadedImage> {
        const canvasRotation = cropperSettings.canvasRotation + loadedImage.exifTransform.rotate;
        const originalSize = {
            width: loadedImage.original.image.naturalWidth,
            height: loadedImage.original.image.naturalHeight,
        };
        if (canvasRotation === 0 && !loadedImage.exifTransform.flip && !cropperSettings.containWithinAspectRatio) {
            return {
                original: {
                    base64: loadedImage.original.base64,
                    image: loadedImage.original.image,
                    size: { ...originalSize },
                },
                transformed: {
                    base64: loadedImage.original.base64,
                    image: loadedImage.original.image,
                    size: { ...originalSize },
                },
                exifTransform: loadedImage.exifTransform,
            };
        }

        const transformedSize = LoadImageService.getTransformedSize(originalSize, loadedImage.exifTransform, cropperSettings);
        const canvas = document.createElement('canvas');
        canvas.width = transformedSize.width;
        canvas.height = transformedSize.height;
        const context = canvas.getContext('2d')!;
        context.setTransform(loadedImage.exifTransform.flip ? -1 : 1, 0, 0, 1, canvas.width / 2, canvas.height / 2);
        context.rotate(Math.PI * (canvasRotation / 2));
        context.drawImage(loadedImage.original.image, -originalSize.width / 2, -originalSize.height / 2);
        const transformedBase64 = canvas.toDataURL();
        const transformedImage = await this.loadImageFromBase64(transformedBase64);
        return {
            original: {
                base64: loadedImage.original.base64,
                image: loadedImage.original.image,
                size: { ...originalSize },
            },
            transformed: {
                base64: transformedBase64,
                image: transformedImage,
                size: {
                    width: transformedImage.width,
                    height: transformedImage.height,
                },
            },
            exifTransform: loadedImage.exifTransform,
        };
    }

    private loadImageFromBase64(imageBase64: string): Promise<HTMLImageElement> {
        return new Promise<HTMLImageElement>((resolve, reject) => {
            const image = new Image();
            image.onload = () => resolve(image);
            image.onerror = reject;
            image.src = imageBase64;
        });
    }

    private static getTransformedSize(originalSize: { width: number; height: number }, exifTransform: ExifTransform, cropperSettings: CropperSettings): Dimensions {
        const canvasRotation = cropperSettings.canvasRotation + exifTransform.rotate;
        if (cropperSettings.containWithinAspectRatio) {
            if (canvasRotation % 2) {
                const minWidthToContain = originalSize.width * cropperSettings.aspectRatio;
                const minHeightToContain = originalSize.height / cropperSettings.aspectRatio;
                return {
                    width: Math.max(originalSize.height, minWidthToContain),
                    height: Math.max(originalSize.width, minHeightToContain),
                };
            } else {
                const minWidthToContain = originalSize.height * cropperSettings.aspectRatio;
                const minHeightToContain = originalSize.width / cropperSettings.aspectRatio;
                return {
                    width: Math.max(originalSize.width, minWidthToContain),
                    height: Math.max(originalSize.height, minHeightToContain),
                };
            }
        }

        if (canvasRotation % 2) {
            return {
                // rotate height and width
                height: originalSize.width,
                width: originalSize.height,
            };
        }
        return {
            width: originalSize.width,
            height: originalSize.height,
        };
    }
}
