/**
 * checks if this component is the current fullscreen component
 */
export function isFullScreen(): boolean {
    const docElement = document as FullscreenDocument;
    if (docElement.fullscreenElement !== undefined) {
        return !!docElement.fullscreenElement;
    } else if (docElement.webkitFullscreenElement !== undefined) {
        return !!docElement.webkitFullscreenElement;
    } else if (docElement.mozFullScreenElement !== undefined) {
        return !!docElement.mozFullScreenElement;
    } else if (docElement.msFullscreenElement !== undefined) {
        return !!docElement.msFullscreenElement;
    }
    return false;
}

/**
 * exit fullscreen
 */
export function exitFullscreen() {
    const docElement = document as FullscreenDocument;
    // exit fullscreen for different browser types
    if (document.exitFullscreen) {
        document.exitFullscreen();
    } else if (docElement.mozCancelFullScreen) {
        docElement.mozCancelFullScreen();
    } else if (docElement.msRequestFullscreen) {
        docElement.msRequestFullscreen();
    } else if (docElement.webkitExitFullscreen) {
        docElement.webkitExitFullscreen();
    }
}

/**
 * enter full screen
 */
export function enterFullscreen(element: any) {
    const target: FullscreenElement = element;
    // requestFullscreen for different browser types
    if (target.requestFullscreen) {
        target.requestFullscreen();
    } else if (target.mozRequestFullScreen) {
        target.mozRequestFullScreen();
    } else if (target.msRequestFullscreen) {
        target.msRequestFullscreen();
    } else if (target.webkitRequestFullscreen) {
        target.webkitRequestFullscreen();
    }
}

type FullscreenDocument = Document & {
    webkitFullscreenElement?: Element | null;
    mozFullScreenElement?: Element | null;
    msFullscreenElement?: Element | null;
    webkitExitFullscreen?: () => void;
    mozCancelFullScreen?: () => void;
    msRequestFullscreen?: () => void;
};

type FullscreenElement = Element & {
    webkitRequestFullscreen?: () => Promise<void> | void;
    mozRequestFullScreen?: () => Promise<void> | void;
    msRequestFullscreen?: () => Promise<void> | void;
};
