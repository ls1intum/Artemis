import { SVG } from '@tumaet/apollon';

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
            const canvas = document.createElement('canvas');
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
 * Trims excess whitespace from an exported SVG while preserving the original global
 * clip coordinates so Artemis can still place the element relative to the full diagram.
 */
export function trimRenderedSVGToContent(renderedSVG: SVG): SVG {
    const parser = new DOMParser();
    const documentFragment = parser.parseFromString(renderedSVG.svg, 'image/svg+xml');
    const svg = documentFragment.documentElement;
    if (!(svg instanceof SVGSVGElement)) {
        return renderedSVG;
    }

    const host = document.createElement('div');
    host.style.position = 'absolute';
    host.style.left = '-9999px';
    host.style.top = '-9999px';
    host.style.visibility = 'hidden';
    host.appendChild(svg);
    document.body.appendChild(host);

    try {
        const bbox = svg.getBBox();
        if (!Number.isFinite(bbox.x) || !Number.isFinite(bbox.y) || !Number.isFinite(bbox.width) || !Number.isFinite(bbox.height) || bbox.width === 0 || bbox.height === 0) {
            return renderedSVG;
        }

        const wrapper = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        wrapper.classList.add('__trim_wrapper__');

        const staticChildren = new Set(['style', 'defs', 'title', 'desc']);
        const childrenToWrap = Array.from(svg.childNodes).filter((child) => {
            return !(child instanceof Element) || !staticChildren.has(child.tagName.toLowerCase());
        });

        for (const child of childrenToWrap) {
            wrapper.appendChild(child);
        }

        svg.appendChild(wrapper);
        wrapper.setAttribute('transform', `translate(${-bbox.x}, ${-bbox.y})`);
        svg.setAttribute('viewBox', `0 0 ${bbox.width} ${bbox.height}`);
        svg.setAttribute('width', `${bbox.width}`);
        svg.setAttribute('height', `${bbox.height}`);

        const serializer = new XMLSerializer();
        return {
            svg: serializer.serializeToString(svg),
            clip: {
                x: bbox.x,
                y: bbox.y,
                width: bbox.width,
                height: bbox.height,
            },
        };
    } catch {
        return renderedSVG;
    } finally {
        document.body.removeChild(host);
    }
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
