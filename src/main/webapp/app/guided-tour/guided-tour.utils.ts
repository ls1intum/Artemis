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
