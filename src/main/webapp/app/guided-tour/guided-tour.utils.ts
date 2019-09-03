export function clickOnElement(selector: string): void {
    const htmlElement = document.querySelector(selector) as HTMLElement;
    if (htmlElement) {
        htmlElement.click();
    }
}
