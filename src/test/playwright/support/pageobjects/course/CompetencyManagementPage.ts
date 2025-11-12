import { Page, expect } from '@playwright/test';

export type CreateCompetencyOptions = {
    title: string;
    description: string;
    taxonomy?: string; // e.g., 'UNDERSTAND', 'APPLY'
    softDueDate?: Date | string;
    /**
     * When true (default), navigate back to the page where createCompetency was called from after creation.
     */
    returnToPrevious?: boolean;
};

export type CreatePrerequisiteOptions = {
    title: string;
    description: string;
    taxonomy?: string;
    softDueDate?: Date | string;
    returnToPrevious?: boolean;
};

export class CompetencyManagementPage {
    constructor(private readonly page: Page) {}

    async goto(courseId: number) {
        await this.page.goto(`/course-management/${courseId}/competency-management`);
    }

    async closeIntroIfVisible(timeout: number = 2000) {
        const closeButton = this.page.locator('#close-button');
        try {
            // Wait briefly for the button to appear (non-fatal if it never does)
            await closeButton.waitFor({ state: 'visible', timeout });
            await closeButton.click();
            // Optionally wait for it to disappear to avoid racing with subsequent actions
            await closeButton.waitFor({ state: 'detached', timeout: 500 }).catch(() => {});
        } catch {
            // Swallow timeout or visibility errors; modal simply wasn't there yet
        }
    }

    private async selectTaxonomy(taxonomy: string) {
        const select = this.page.getByLabel('Taxonomy');
        // First try the fast path which some tests used previously
        try {
            await select.selectOption(`2: ${taxonomy}`);
            return;
        } catch {
            // fall through to more robust strategy
        }

        // Fallback: iterate over option elements and match by visible text
        const options = select.locator('option');
        const count = await options.count();
        const target = taxonomy.trim().toLowerCase();
        for (let i = 0; i < count; i++) {
            const text = (await options.nth(i).textContent())?.trim();
            if (!text) continue;
            const textLower = text.toLowerCase();
            if (textLower === target || textLower.includes(target)) {
                await select.selectOption({ label: text });
                return;
            }
        }
        throw new Error(`Taxonomy option "${taxonomy}" not found`);
    }

    private async setSoftDueDate(softDueDate: Date | string) {
        try {
            const target = typeof softDueDate === 'string' ? new Date(softDueDate) : softDueDate;
            if (!isNaN(target.getTime())) {
                const now = new Date();
                let monthDiff = (target.getFullYear() - now.getFullYear()) * 12 + (target.getMonth() - now.getMonth());
                // open datepicker
                await this.page.locator('.btn.position-absolute').first().click();
                // only navigate forward to reduce complexity/flakiness
                while (monthDiff > 0) {
                    await this.page.getByRole('button', { name: 'Next month' }).click();
                    monthDiff--;
                }
                await this.page.getByText(String(target.getDate())).click();
            }
        } catch {
            // ignore date errors
        }
    }

    async createCompetency(courseId: number, options: CreateCompetencyOptions) {
        const { title, description, taxonomy, softDueDate, returnToPrevious = true } = options;
        const previousUrl = this.page.url();

        await this.goto(courseId);
        await this.closeIntroIfVisible();

        // Open create form
        await this.page.getByRole('link', { name: 'Create competency' }).click();

        // Fill basic fields
        await this.page.getByRole('textbox', { name: 'Title' }).fill(title);
        await this.page.getByRole('textbox', { name: 'Editor content' }).fill(description);

        // Optional: set soft due date
        if (softDueDate) {
            await this.setSoftDueDate(softDueDate);
        }
        // Optional taxonomy selection using robust helper
        if (taxonomy) {
            await this.selectTaxonomy(taxonomy);
        }

        // Submit
        await this.page.getByRole('button', { name: 'Submit' }).click();

        // Verify row appears
        await expect(this.page.getByRole('cell', { name: new RegExp(title, 'i') })).toBeVisible();

        // Navigate back to previous page if requested
        if (returnToPrevious && previousUrl) {
            await this.page.goto(previousUrl);
        }
    }

    async createPrerequisite(courseId: number, options: CreatePrerequisiteOptions) {
        const { title, description, taxonomy, softDueDate, returnToPrevious = true } = options;
        const previousUrl = this.page.url();

        await this.goto(courseId);
        await this.closeIntroIfVisible();

        // Open create prerequisite form
        await this.page.getByRole('link', { name: 'Create prerequisite' }).click();

        // Fill basic fields (note: field labelled 'Prerequisites' in UI)
        await this.page.getByRole('textbox', { name: 'Prerequisites' }).fill(title);
        await this.page.getByRole('textbox', { name: 'Editor content' }).fill(description);

        // Optional soft due date
        if (softDueDate) {
            await this.setSoftDueDate(softDueDate);
        }

        // Optional taxonomy
        if (taxonomy) {
            await this.selectTaxonomy(taxonomy);
        }

        // Submit
        await this.page.getByRole('button', { name: 'Submit' }).click();

        // Verify creation: prerequisite appears as link
        await expect(this.page.getByRole('link', { name: new RegExp(title, 'i') })).toBeVisible();

        if (returnToPrevious && previousUrl) {
            await this.page.goto(previousUrl);
        }
    }
}
