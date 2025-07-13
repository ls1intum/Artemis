import { resizeCanvas } from './resize.utils';

describe('resizeCanvas', () => {
    let canvas: HTMLCanvasElement;

    beforeEach(() => {
        canvas = document.createElement('canvas');
        canvas.width = 100;
        canvas.height = 100;

        const context = canvas.getContext('2d');
        if (context) {
            const imageData = context.createImageData(100, 100);
            for (let i = 0; i < imageData.data.length; i++) {
                imageData.data[i] = 255; // Fill with white pixels
            }
            context.putImageData(imageData, 0, 0);
        }
    });

    it('should resize the canvas to the specified dimensions', () => {
        resizeCanvas(canvas, 50, 50);

        expect(canvas.width).toBe(50);
        expect(canvas.height).toBe(50);

        const context = canvas.getContext('2d');
        expect(context).not.toBeNull();

        if (context) {
            const resizedImageData = context.getImageData(0, 0, 50, 50);
            expect(resizedImageData.width).toBe(50);
            expect(resizedImageData.height).toBe(50);
        }
    });
});
