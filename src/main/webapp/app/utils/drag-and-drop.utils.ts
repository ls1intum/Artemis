/**
 *  The background image for drag and drop quizzes is dynamically adjusted through scss, therefore we have to resize the click layer to the same width and height as the background image
 */
export function resizeImage() {
    /* set timeout as workaround to render all necessary elements */
    setTimeout(() => {
        const image = document.querySelector('.background-area jhi-secured-image img') as HTMLImageElement;
        const clickLayer = document.getElementsByClassName('click-layer').item(0) as HTMLElement;
        if (clickLayer) {
            clickLayer.style.width = image.width + 'px';
            clickLayer.style.height = image.height + 'px';
        }
    }, 100);
}
