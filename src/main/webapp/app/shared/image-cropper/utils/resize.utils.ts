/*
 * Hermite resize - fast image resize/resample using Hermite filter.
 * https://github.com/viliusle/Hermite-resize
 */
export function resizeCanvas(canvas: HTMLCanvasElement, width: number, height: number) {
    const widthSource = canvas.width;
    const heightSource = canvas.height;
    width = Math.round(width);
    height = Math.round(height);

    const ratioWidth = widthSource / width;
    const ratioHeight = heightSource / height;
    const ratioWidthHalf = Math.ceil(ratioWidth / 2);
    const ratioHeightHalf = Math.ceil(ratioHeight / 2);

    const context = canvas.getContext('2d');
    if (context) {
        const image = context.getImageData(0, 0, widthSource, heightSource);
        const newImage = context.createImageData(width, height);
        const data = image.data;
        const newData = newImage.data;

        for (let j = 0; j < height; j++) {
            for (let i = 0; i < width; i++) {
                const x2 = (i + j * width) * 4;
                const center_y = j * ratioHeight;
                let weight = 0;
                let weights = 0;
                let weights_alpha = 0;
                let gx_r = 0;
                let gx_g = 0;
                let gx_b = 0;
                let gx_a = 0;

                const xx_start = Math.floor(i * ratioWidth);
                const yy_start = Math.floor(j * ratioHeight);
                let xx_stop = Math.ceil((i + 1) * ratioWidth);
                let yy_stop = Math.ceil((j + 1) * ratioHeight);
                xx_stop = Math.min(xx_stop, widthSource);
                yy_stop = Math.min(yy_stop, heightSource);

                for (let yy = yy_start; yy < yy_stop; yy++) {
                    const dy = Math.abs(center_y - yy) / ratioHeightHalf;
                    const center_x = i * ratioWidth;
                    const w0 = dy * dy; // pre-calc part of w
                    for (let xx = xx_start; xx < xx_stop; xx++) {
                        const dx = Math.abs(center_x - xx) / ratioWidthHalf;
                        const w = Math.sqrt(w0 + dx * dx);
                        if (w >= 1) {
                            // pixel too far
                            continue;
                        }
                        // hermite filter
                        weight = 2 * w * w * w - 3 * w * w + 1;
                        const pos_x = 4 * (xx + yy * widthSource);
                        // alpha
                        gx_a += weight * data[pos_x + 3];
                        weights_alpha += weight;
                        // colors
                        if (data[pos_x + 3] < 255) {
                            weight = (weight * data[pos_x + 3]) / 250;
                        }
                        gx_r += weight * data[pos_x];
                        gx_g += weight * data[pos_x + 1];
                        gx_b += weight * data[pos_x + 2];
                        weights += weight;
                    }
                }
                newData[x2] = gx_r / weights;
                newData[x2 + 1] = gx_g / weights;
                newData[x2 + 2] = gx_b / weights;
                newData[x2 + 3] = gx_a / weights_alpha;
            }
        }

        canvas.width = width;
        canvas.height = height;

        // draw
        context.putImageData(newImage, 0, 0);
    }
}
