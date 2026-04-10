import { Page, expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the Course Onboarding Wizard.
 */
export class CourseOnboardingPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Verifies that the onboarding wizard is displayed.
     */
    async expectWizardVisible() {
        await expect(this.page.locator('.onboarding-wizard')).toBeVisible();
    }

    /**
     * @returns A locator for the currently active step indicator element.
     */
    getActiveStepItem() {
        return this.page.locator('.step-item.active');
    }

    /**
     * @returns All completed step items.
     */
    getCompletedStepItems() {
        return this.page.locator('.step-item.completed');
    }

    /**
     * @returns The step indicator container.
     */
    getStepIndicator() {
        return this.page.locator('.step-indicator');
    }

    /**
     * Clicks the "Next" button to advance to the next step (saves current step).
     */
    async clickNext() {
        await this.page.locator('.footer-right .btn-primary').click();
    }

    /**
     * Clicks the "Previous" button to go back to the previous step.
     */
    async clickPrevious() {
        await this.page.locator('.footer-left .btn-secondary').click();
    }

    /**
     * Clicks the "Finish Setup" button (saves onboardingDone and advances to Explore step).
     */
    async clickFinishSetup() {
        await this.page.locator('.footer-right .btn-success').click();
    }

    /**
     * Clicks the "Go to Course" button on the Explore step.
     */
    async clickGoToCourse() {
        await this.page.locator('.footer-right .btn-primary').click();
    }

    /**
     * Verifies the "Previous" button is not visible (first step).
     */
    async expectNoPreviousButton() {
        await expect(this.page.locator('.footer-left .btn-secondary')).toBeHidden();
    }

    /**
     * Verifies the "Previous" button is visible.
     */
    async expectPreviousButtonVisible() {
        await expect(this.page.locator('.footer-left .btn-secondary')).toBeVisible();
    }

    /**
     * Verifies the "Finish Setup" button is visible (Assessment step).
     */
    async expectFinishButtonVisible() {
        await expect(this.page.locator('.footer-right .btn-success')).toBeVisible();
    }

    /**
     * Verifies the "Next" button is visible.
     */
    async expectNextButtonVisible() {
        await expect(this.page.locator('.footer-right .btn-primary')).toBeVisible();
    }

    /**
     * Verifies the explore cards are visible on the Explore step.
     */
    async expectExploreCardsVisible() {
        await expect(this.page.locator('.explore-card').first()).toBeVisible();
    }

    /**
     * Gets the content area of the wizard.
     */
    getContent() {
        return this.page.locator('.onboarding-content');
    }
}
