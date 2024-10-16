import { Locator, Page, expect } from '@playwright/test';
import { PROGRAMMING_EXERCISE_BASE, ProgrammingLanguage } from '../../../constants';
import { Dayjs } from 'dayjs';

const OWL_DATEPICKER_ARIA_LABEL_DATE_FORMAT = 'MMMM D, YYYY';

export class ProgrammingExerciseCreationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async setTitle(title: string) {
        await this.page.locator('#field_title').fill(title);
    }

    async changeEditMode() {
        await this.page.locator('#switch-edit-mode-button').click();
    }

    async setShortName(shortName: string) {
        const shortNameField = this.page.locator('#field_shortName');
        await shortNameField.clear();
        await shortNameField.fill(shortName);
    }

    async setProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        await this.page.locator('#field_programmingLanguage').selectOption(programmingLanguage);
    }

    async setPackageName(packageName: string) {
        const packageNameField = this.page.locator('#field_packageName');
        await packageNameField.clear();
        await packageNameField.fill(packageName);
    }

    async setPoints(points: number) {
        const pointsField = this.page.locator('#field_points');
        await pointsField.clear();
        await pointsField.fill(points.toString());
    }

    async checkAllowOnlineEditor() {
        await this.page.locator('#field_allowOnlineEditor').check();
    }

    async generate() {
        const responsePromise = this.page.waitForResponse(`${PROGRAMMING_EXERCISE_BASE}/setup`);
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }

    async import() {
        const responsePromise = this.page.waitForResponse(`${PROGRAMMING_EXERCISE_BASE}/import/*`);
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }

    /**
     * Sets the Due Date field by using the owl datepicker
     * @param date
     * */
    async setDueDate(date: Dayjs) {
        await this.page.locator('#programming-exercise-due-date-picker').click();

        // Makes sure that popup is visible before we choose a date
        await this.page.locator('.owl-dt-popup').waitFor({ state: 'visible' });

        const ariaLabelDate = date.format(OWL_DATEPICKER_ARIA_LABEL_DATE_FORMAT);
        await this.page.locator(`td[aria-label="${ariaLabelDate}"]`).click();

        // Ensure the date picker is closed after setting the date
        while (await this.page.locator('.owl-dt-popup').isVisible()) {
            await this.page.locator('.owl-dt-control-content.owl-dt-control-button-content', { hasText: 'Set' }).dispatchEvent('click');
            await this.page.waitForTimeout(500);
        }
        await expect(this.page.locator('.owl-dt-popup')).not.toBeVisible();
    }

    /**
     * Uses an element that is affected by the scrolling of the page as reference to determine if the page is still scrolling
     *
     * Here we are using the headline of the page as reference
     */
    async waitForPageToFinishScrolling(maxTimeout: number = 5000) {
        const elementOnPageAffectedByScroll = this.page.locator('h2');
        let isScrolling = true;
        const startTime = Date.now();

        while (isScrolling) {
            const initialPosition = await elementOnPageAffectedByScroll.boundingBox();
            await this.page.waitForTimeout(100); // give the page a short time to scroll
            const newPosition = await elementOnPageAffectedByScroll.boundingBox();

            isScrolling = initialPosition?.y !== newPosition?.y;

            const isWaitingForScrollExceedingTimeout = Date.now() - startTime > maxTimeout;
            if (isWaitingForScrollExceedingTimeout) {
                throw new Error(`Aborting waiting for scroll end - page is still scrolling after ${maxTimeout}ms`);
            }
        }
    }

    async clickFormStatusBarSection(sectionId: number) {
        const searchedSectionId = `#status-bar-section-item-${sectionId}`;
        const sectionStatusBarLocator: Locator = this.page.locator(searchedSectionId);
        expect(await sectionStatusBarLocator.isVisible()).toBeTruthy();
        await sectionStatusBarLocator.click();
        await this.waitForPageToFinishScrolling();
    }

    /**
     * Verifies that the locator is visible in the viewport and not hidden by another element
     * (e.g. could be hidden by StatusBar / Navbar)
     *
     * {@link toBeHidden} and {@link toBeVisible} do not solve this problem
     * @param locator
     */
    private async verifyLocatorIsVisible(locator: Locator) {
        const initialPosition = await locator.boundingBox();
        await locator.click(); // scrolls to the locator if needed (e.g. if hidden by another element)
        const newPosition = await locator.boundingBox();
        expect(initialPosition).toEqual(newPosition);
    }

    async checkIsHeadlineVisibleToUser(searchedHeadlineDisplayText: string, expected: boolean) {
        const headlineLocator = this.page.getByRole('heading', { name: searchedHeadlineDisplayText }).first();

        if (expected) {
            await expect(headlineLocator).toBeInViewport({ ratio: 1 });
            /** additional check because {@link toBeInViewport} is too inaccurate */
            await this.verifyLocatorIsVisible(headlineLocator);
        } else {
            await expect(headlineLocator).not.toBeInViewport();
        }
    }
}
