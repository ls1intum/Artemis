/**
 * A {@link Document} extended with the vendor-prefixed fullscreen members used across browsers.
 */
type VendorPrefixedDocument = Document & {
    webkitFullscreenElement?: Element;
    mozFullScreenElement?: Element;
    msFullscreenElement?: Element;
    mozCancelFullScreen?: () => void;
    msRequestFullscreen?: () => void;
    webkitExitFullscreen?: () => void;
};

/**
 * An element extended with the vendor-prefixed request-fullscreen members used across browsers.
 */
type VendorPrefixedElement = HTMLElement & {
    mozRequestFullScreen?: () => void;
    msRequestFullscreen?: () => void;
    webkitRequestFullscreen?: () => void;
};

/**
 * checks if this component is the current fullscreen component
 */
export function isFullScreen(): boolean {
    const docElement = document as VendorPrefixedDocument;
    // check if this component is the current fullscreen component for different browser types
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
    const docElement = document as VendorPrefixedDocument;
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
export function enterFullscreen(element: VendorPrefixedElement) {
    // requestFullscreen for different browser types
    if (element.requestFullscreen) {
        element.requestFullscreen();
    } else if (element.mozRequestFullScreen) {
        element.mozRequestFullScreen();
    } else if (element.msRequestFullscreen) {
        element.msRequestFullscreen();
    } else if (element.webkitRequestFullscreen) {
        element.webkitRequestFullscreen();
    }
}
