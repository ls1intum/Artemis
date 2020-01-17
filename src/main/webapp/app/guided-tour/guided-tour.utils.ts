import { Orientation } from 'app/guided-tour/guided-tour.constants';

/**
 * Helper function that triggers a click event on the defined element
 * @param selector: css selector to query the required element
 */
export function clickOnElement(selector: string): void {
    const htmlElement = document.querySelector(selector) as HTMLElement;
    if (htmlElement) {
        htmlElement.click();
    }
}

/**
 * Helper function that calculates the top position of the given element
 * @param element
 */
export function calculateTopOffset(element: HTMLElement): number {
    let topOffset = element.offsetTop;
    while (element.offsetParent) {
        element = element.offsetParent as HTMLElement;
        topOffset += element.offsetTop;
    }
    return topOffset;
}

/**
 * Helper function that calculates the left position of the given element
 * @param element
 */
export function calculateLeftOffset(element: HTMLElement): number {
    let leftOffset = element.offsetLeft;
    while (element.offsetParent) {
        element = element.offsetParent as HTMLElement;
        leftOffset += element.offsetLeft;
    }
    return leftOffset;
}

/**
 * Helper function that determines whether the given tour step element is in the view port horizontally
 * @param orientation   orientation of the current tour step
 * @param left          left position of the given element
 * @param width         width of the given element
 * @param tourStepWidth width of the tour step
 */
export function isElementInViewPortHorizontally(orientation: Orientation, left: number, width: number, tourStepWidth: number): boolean {
    let elementInViewPort = true;
    switch (orientation) {
        case Orientation.TOPLEFT: {
            elementInViewPort = left + width + tourStepWidth < window.innerWidth;
            break;
        }
        case Orientation.BOTTOMLEFT: {
            elementInViewPort = left + width + tourStepWidth < window.innerWidth;
            break;
        }
        case Orientation.LEFT: {
            elementInViewPort = left - tourStepWidth > window.screenLeft;
            break;
        }
        case Orientation.TOPRIGHT: {
            elementInViewPort = left + width - tourStepWidth > window.screenLeft;
            break;
        }
        case Orientation.BOTTOMRIGHT: {
            elementInViewPort = left + width - tourStepWidth > window.screenLeft;
            break;
        }
        case Orientation.RIGHT: {
            elementInViewPort = left + width + tourStepWidth < window.innerWidth;
            break;
        }
    }
    return elementInViewPort;
}
