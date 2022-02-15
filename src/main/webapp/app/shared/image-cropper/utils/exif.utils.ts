import { ExifTransform } from '../interfaces/exif-transform.interface';

// Black 2x1 JPEG, with the following meta information set:
// - EXIF Orientation: 6 (Rotated 90° CCW)
// Source: https://github.com/blueimp/JavaScript-Load-Image
const testAutoOrientationImageURL =
    'data:image/jpeg;base64,/9j/4QAiRXhpZgAATU0AKgAAAAgAAQESAAMAAAABAAYAAAA' +
    'AAAD/2wCEAAEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBA' +
    'QEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE' +
    'BAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAf/AABEIAAEAAgMBEQACEQEDEQH/x' +
    'ABKAAEAAAAAAAAAAAAAAAAAAAALEAEAAAAAAAAAAAAAAAAAAAAAAQEAAAAAAAAAAAAAAAA' +
    'AAAAAEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwA/8H//2Q==';

export function supportsAutomaticRotation(): Promise<boolean> {
    return new Promise((resolve) => {
        const img = new Image();
        img.onload = () => {
            // Check if browser supports automatic image orientation:
            const supported = img.width === 1 && img.height === 2;
            resolve(supported);
        };
        img.src = testAutoOrientationImageURL;
    });
}

export function getTransformationsFromExifData(exifRotationOrBase64Image: number | string): ExifTransform {
    if (typeof exifRotationOrBase64Image === 'string') {
        exifRotationOrBase64Image = getExifRotation(exifRotationOrBase64Image);
    }
    switch (exifRotationOrBase64Image) {
        case 2:
            return { rotate: 0, flip: true };
        case 3:
            return { rotate: 2, flip: false };
        case 4:
            return { rotate: 2, flip: true };
        case 5:
            return { rotate: 1, flip: true };
        case 6:
            return { rotate: 1, flip: false };
        case 7:
            return { rotate: 3, flip: true };
        case 8:
            return { rotate: 3, flip: false };
        default:
            return { rotate: 0, flip: false };
    }
}

function getExifRotation(imageBase64: string): number {
    const view = new DataView(base64ToArrayBuffer(imageBase64));
    if (view.getUint16(0, false) !== 0xffd8) {
        return -2;
    }
    const length = view.byteLength;
    let offset = 2;
    while (offset < length) {
        if (view.getUint16(offset + 2, false) <= 8) {
            return -1;
        }
        const marker = view.getUint16(offset, false);
        offset += 2;
        if (marker === 0xffe1) {
            if (view.getUint32((offset += 2), false) !== 0x45786966) {
                return -1;
            }

            const little = view.getUint16((offset += 6), false) === 0x4949;
            offset += view.getUint32(offset + 4, little);
            const tags = view.getUint16(offset, little);
            offset += 2;
            for (let i = 0; i < tags; i++) {
                if (view.getUint16(offset + i * 12, little) === 0x0112) {
                    return view.getUint16(offset + i * 12 + 8, little);
                }
            }
            // tslint:disable-next-line:no-bitwise
        } else if ((marker & 0xff00) !== 0xff00) {
            break;
        } else {
            offset += view.getUint16(offset, false);
        }
    }
    return -1;
}

function base64ToArrayBuffer(imageBase64: string) {
    imageBase64 = imageBase64.replace(/^data\:([^\;]+)\;base64,/gim, '');
    const binaryString = window.atob(imageBase64);
    const len = binaryString.length;
    const bytes = new Uint8Array(len);
    for (let i = 0; i < len; i++) {
        bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes.buffer;
}
