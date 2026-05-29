import { Dimensions } from './dimensions.interface';
import { ExifTransform } from './exif-transform.interface';

export interface LoadedImage {
    original: {
        base64: string;
        image: HTMLImageElement;
        size: Dimensions;
    };
    transformed?: {
        base64: string;
        image: HTMLImageElement;
        size: Dimensions;
    };
    exifTransform: ExifTransform;
}
