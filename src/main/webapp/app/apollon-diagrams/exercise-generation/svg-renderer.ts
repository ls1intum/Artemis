import { RenderedSVG } from '@ls1intum/apollon';

interface Size {
    width: number;
    height: number;
}

export function convertRenderedSVGToPNG(renderedSVG: RenderedSVG): Promise<Blob> {
    return new Promise((resolve, reject) => {
        const { width, height } = renderedSVG.size;

        const blob = new Blob([renderedSVG.svg], { type: 'image/svg+xml' });
        const blobUrl = URL.createObjectURL(blob);

        const image = new Image();
        image.width = width;
        image.height = height;
        image.src = blobUrl;

        image.onload = () => {
            const canvas = document.createElement('canvas');
            canvas.style.width = `${width}px`;
            canvas.style.height = `${height}px`;

            const scale = 3;
            canvas.width = width * scale;
            canvas.height = height * scale;

            const context = canvas.getContext('2d')!;
            context.scale(scale, scale);
            context.drawImage(image, 0, 0);

            toPNGBlob(canvas, resolve);
        };

        image.onerror = error => {
            reject(error);
        };
    });
}

// Some browsers (such as IE or Edge) don't support the HTMLCanvasElement.toBlob() method,
// so we use the (much more inefficient) toDataURL() method as a fallback
function toPNGBlob(canvas: HTMLCanvasElement, callback: (blob: Blob) => void) {
    if (typeof canvas.toBlob === 'function') {
        canvas.toBlob(callback);
    } else {
        setTimeout(() => {
            const binaryRepresentation = atob(canvas.toDataURL().split(',')[1]);
            const length = binaryRepresentation.length;
            const buffer = new Uint8Array(length);

            for (let i = 0; i < length; i++) {
                buffer[i] = binaryRepresentation.charCodeAt(i);
            }

            callback(new Blob([buffer], { type: 'image/png' }));
        });
    }
}
