import { Locator, expect } from '@playwright/test';
import { PROGRAMMING_EXERCISE_BASE, ProgrammingLanguage } from '../../../constants';
import { Dayjs } from 'dayjs';
import { AbstractExerciseCreationPage } from '../AbstractExerciseCreationPage';

// Date format expected by ExerciseTimelineComponent#handleManualInput (strict parse against /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/).
const TIMELINE_DATE_FORMAT = 'DD.MM.YYYY HH:mm';

export class ProgrammingExerciseCreationPage extends AbstractExerciseCreationPage {
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
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes(`${PROGRAMMING_EXERCISE_BASE}/setup`));
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }

    async import() {
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes(`${PROGRAMMING_EXERCISE_BASE}/import/`));
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }

    /**
     * Sets the Due Date field on the unified exercise timeline (PrimeNG p-datepicker rendered inside
     * <jhi-exercise-timeline>). The timeline assigns the inputId per visible item dynamically
     * (e.g. datepicker-0, datepicker-1, ...), so we locate the input through its associated label
     * and then fill it in the format expected by ExerciseTimelineComponent#handleManualInput.
     *
     * @param date the due date to set
     */
    async setDueDate(date: Dayjs) {
        const dueDateInput = this.page.getByLabel('Due Date', { exact: true });
        await expect(dueDateInput).toBeEnabled();
        await dueDateInput.fill(date.format(TIMELINE_DATE_FORMAT));
        // Tab out so PrimeNG flushes the pending onInput event (which triggers handleManualInput
        // and writes the parsed date back into the timeline's date model) before the form is submitted.
        await dueDateInput.press('Tab');
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
