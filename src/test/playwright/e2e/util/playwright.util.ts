import { Page } from '@playwright/test';

/**
 * Checks if an element is fully visible within the current viewport (it is not sufficient to use .toBeVisible(),
 * as this does not check if the element is fully visible in the viewport but only if it exists in the DOM).
 *
 * @param page The Playwright page object
 * @param selector CSS selector for the element to check
 * @returns Promise<boolean> True if the element is fully visible in the viewport
 */
export async function isElementInViewport(page: Page, selector: string): Promise<boolean> {
    return page.evaluate((selector) => {
        const el = document.querySelector(selector);
        if (!el) return false;
        const rect = el.getBoundingClientRect();
        return rect.top >= 0 && rect.left >= 0 && rect.bottom <= window.innerHeight && rect.right <= window.innerWidth;
    }, selector);
}
