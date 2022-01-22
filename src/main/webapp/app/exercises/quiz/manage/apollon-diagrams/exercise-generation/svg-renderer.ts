import { SVG } from '@ls1intum/apollon';

/**
 * Converts svg to png.
 */
export function convertRenderedSVGToPNG(renderedSVG: SVG): Promise<Blob> {
    return new Promise((resolve, reject) => {
        const { width, height } = renderedSVG.clip;

        const blob = new Blob([renderedSVG.svg], { type: 'image/svg+xml' });
        const blobUrl = URL.createObjectURL(blob);

        const image = new Image();
        image.width = width;
        image.height = height;
        image.src = blobUrl;

        image.onload = () => {
            // Important Notice: canvas is intentionally a variable because of optimization steps in Webpack
            // In the resulting JS production code, the function 'toPNGBlob' below is inlined and as part of
            // the typeof comparison, canvas somehow needs to be reassigned to itself which produces a run-time
            // error, when canvas is defined as const. Unfortunately, this error does not occur during development
            let canvas: HTMLCanvasElement;
            canvas = document.createElement('canvas');
            canvas.style.width = `${width}px`;
            canvas.style.height = `${height}px`;

            const scale = 1.5;
            canvas.width = width * scale;
            canvas.height = height * scale;

            const context = canvas.getContext('2d')!;
            context.scale(scale, scale);
            context.drawImage(image, 0, 0);

            toPNGBlob(canvas, resolve);
        };

        image.onerror = (error) => {
            reject(error);
        };
    });
}

/**
 * Fallback for HTMLCanvasElement.toBlob().
 */
// Some browsers (such as IE or Edge) don't support the HTMLCanvasElement.toBlob() method,
// so we use the (much more inefficient) toDataURL() method as a fallback
function toPNGBlob(canvas: HTMLCanvasElement, callback: (blob: Blob) => void) {
    if (typeof canvas.toBlob === 'function') {
        canvas.toBlob(callback);
    } else {
        setTimeout(() => {
            const binaryRepresentation = window.atob(canvas.toDataURL().split(',')[1]);
            const length = binaryRepresentation.length;
            const buffer = new Uint8Array(length);

            for (let i = 0; i < length; i++) {
                buffer[i] = binaryRepresentation.charCodeAt(i);
            }

            callback(new Blob([buffer], { type: 'image/png' }));
        });
    }
}
