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

/**
 * Determines if the given tour step url matches with the current router url
 * @param pageUrl   current page url
 * @param tourStepUrl   tour step url as string
 */
export function determineUrlMatching(pageUrl: string, tourStepUrl: string) {
    if (tourStepUrl.indexOf('?') !== -1) {
        return pageUrl.match(tourStepUrl.slice(0, tourStepUrl.indexOf('?')));
    } else {
        return pageUrl.match(tourStepUrl);
    }
}

/**
 * Helper function to retrieve the parameters of a URL string
 * @param url   url as string
 */
export function getUrlParams(url: string): string {
    if (url.indexOf('?') !== -1) {
        return url.slice(url.indexOf('?'), url.length);
    } else {
        return '';
    }
}

/**
 * Checks if the page url matches with the matching url result
 * @param pageUrl
 * @param matchingUrl
 */
export function checkPageUrlEnding(pageUrl: string, matchingUrl: string): boolean {
    let tempPageUrl = pageUrl;
    if (pageUrl.indexOf('?') !== -1) {
        tempPageUrl = pageUrl.slice(0, pageUrl.indexOf('?'));
    }
    return tempPageUrl.endsWith(matchingUrl);
}
