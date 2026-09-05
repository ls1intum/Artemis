import { Page, expect } from '@playwright/test';

/**
 * Page object for the "Create Variant with AI" wizard/modal
 * (`exercise-variant-ai-modal-wizard.component.ts`). Drives the 3-step wizard
 * (Select → Configure → Placement) and the live generation/result steps.
 */
export class ExerciseVariantAiWizard {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async expectOpen() {
        await expect(this.page.locator('.wizard-step-indicator')).toBeVisible();
    }

    /** Step 1: select the "Application Domain" adaptation and advance. */
    async selectDomainAdaptationAndContinue() {
        await this.page.locator('.option-card', { hasText: 'Application Domain' }).click();
        await this.page.getByRole('button', { name: 'Next' }).click();
    }

    /** Step 2: enter the new domain and advance to placement. */
    async fillDomainAndContinue(domain: string) {
        await this.page.getByPlaceholder(/space exploration/).fill(domain);
        await this.page.getByRole('button', { name: 'Next' }).click();
    }

    /** Step 3: choose standalone placement and start generation. */
    async chooseStandaloneAndGenerate() {
        await this.page.locator('.placement-option', { hasText: 'standalone' }).click();
        await this.page.getByRole('button', { name: 'Generate Variant' }).click();
    }

    /** Step 4: the live generation timeline is shown. */
    async expectGenerating() {
        await expect(this.page.getByTestId('variant-wizard-generating')).toBeVisible();
    }

    /** Detaches the UI from the running job ("Run in background") without cancelling it. */
    async runInBackground() {
        await this.page.getByTestId('variant-wizard-run-in-background').click();
        // The wizard's generation panel disappears once the dialog closes (scoped to the wizard rather than the
        // generic `p-dialog`, of which PrimeNG pre-renders many).
        await expect(this.page.getByTestId('variant-wizard-generating')).toBeHidden();
    }

    /**
     * Step 5: wait for the successful result and assert the planned variant title is shown in the flow card.
     * @param expectedTitle the deterministic planned title the mock LLM produces
     */
    async expectResultWithTitle(expectedTitle: string, timeout = 90_000) {
        await expect(this.page.getByTestId('variant-wizard-result')).toBeVisible({ timeout });
        await expect(this.page.getByTestId('variant-wizard-flow-target')).toHaveText(expectedTitle);
    }

    async close() {
        await this.page.getByRole('button', { name: 'Close' }).click();
    }
}
