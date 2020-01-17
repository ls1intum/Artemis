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
 * Check if the current tour step has a bottom orientation
 * @return true if the current tour step orientation is bottom, otherwise false
 */
export function isBottom(): boolean {
    if (this.currentTourStep && this.currentTourStep.orientation) {
        return (
            this.currentTourStep.orientation === Orientation.BOTTOM ||
            this.currentTourStep.orientation === Orientation.BOTTOMLEFT ||
            this.currentTourStep.orientation === Orientation.BOTTOMRIGHT
        );
    }
    return false;
}

/**
 * Check if the current tour step has a top orientation
 * @return true if the current tour step orientation is bottom, otherwise false
 */
export function isTop(): boolean {
    if (this.currentTourStep && this.currentTourStep.orientation) {
        return (
            this.currentTourStep.orientation === Orientation.TOP ||
            this.currentTourStep.orientation === Orientation.TOPLEFT ||
            this.currentTourStep.orientation === Orientation.TOPRIGHT
        );
    }
    return false;
}

/**
 * Check if the current tour step has a left orientation
 * @return true if the current tour step orientation is left, otherwise false
 */
export function isLeft(): boolean {
    if (this.currentTourStep && this.currentTourStep.orientation) {
        return (
            this.currentTourStep.orientation === Orientation.LEFT ||
            this.currentTourStep.orientation === Orientation.TOPLEFT ||
            this.currentTourStep.orientation === Orientation.BOTTOMLEFT
        );
    }
    return false;
}

/**
 * Check if the current tour step has a right orientation
 * @return true if the current tour step orientation is right, otherwise false
 */
export function isRight(): boolean {
    if (this.currentTourStep && this.currentTourStep.orientation) {
        return (
            this.currentTourStep.orientation === Orientation.RIGHT ||
            this.currentTourStep.orientation === Orientation.TOPRIGHT ||
            this.currentTourStep.orientation === Orientation.BOTTOMRIGHT
        );
    }
    return false;
}

export function calculateOffset(element: HTMLElement, offset: number): number {
    while (element.offsetParent) {
        element = element.offsetParent as HTMLElement;
        offset += offset;
    }
    return offset;
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
 * Flips the orientation of the current tour step horizontally
 */
export function flipOrientation(): void {
    if (this.isLeft()) {
        switch (this.currentTourStep.orientation) {
            case Orientation.LEFT: {
                this.orientation = Orientation.RIGHT;
                break;
            }
            case Orientation.TOPLEFT: {
                this.orientation = Orientation.TOPRIGHT;
                break;
            }
            case Orientation.BOTTOMLEFT: {
                this.orientation = Orientation.BOTTOMRIGHT;
                break;
            }
        }
    } else if (this.isRight()) {
        switch (this.currentTourStep.orientation) {
            case Orientation.RIGHT: {
                this.orientation = Orientation.LEFT;
                break;
            }
            case Orientation.TOPRIGHT: {
                this.orientation = Orientation.TOPLEFT;
                break;
            }
            case Orientation.BOTTOMRIGHT: {
                this.orientation = Orientation.BOTTOMLEFT;
                break;
            }
        }
    }
}
