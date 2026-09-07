import { Page, expect } from '@playwright/test';

/**
 * The "Export Repos" dialog reached from the exercise scores page and from the course exercises overview.
 *
 * The checkboxes are addressed by their form control name rather than their label, because the labels are translated
 * and a test that matches on translated text breaks as soon as the wording is improved.
 */
export class ProgrammingExerciseExportDialog {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Opens the export dialog from the scores page. The export actions live in a popover behind the page's Export
     * button, and the popover is rendered into the document body, so the dialog trigger only exists once it is open.
     */
    async open() {
        await this.page.locator('button[jhi-exercise-action-button]').filter({ hasText: 'Export' }).first().click();
        const repositoryExportButton = this.page.locator('jhi-programming-assessment-repo-export button');
        await repositoryExportButton.waitFor({ state: 'visible' });
        await repositoryExportButton.click();
        await this.dialog().waitFor({ state: 'visible' });
    }

    dialog() {
        return this.page.getByRole('dialog');
    }

    /**
     * Ticks or unticks one of the export options.
     *
     * @param name    the form control name, e.g. `combineStudentCommits`
     * @param checked the state the checkbox should end up in
     */
    async setOption(name: string, checked: boolean) {
        const checkbox = this.dialog().locator(`input[name="${name}"]`);
        await checkbox.waitFor({ state: 'visible' });
        await checkbox.setChecked(checked);
        expect(await checkbox.isChecked(), `the option ${name} must end up ${checked ? 'ticked' : 'unticked'}`).toBe(checked);
    }

    /** Unticks every option that rewrites the exported repository, so the export is a faithful copy. */
    async disableAllRewritingOptions() {
        for (const option of ['filterLateSubmissions', 'excludePracticeSubmissions', 'addParticipantName', 'combineStudentCommits', 'anonymizeRepository', 'normalizeCodeStyle']) {
            await this.setOption(option, false);
        }
    }

    /** Fills the comma separated list of logins or team short names. */
    async setParticipantIdentifiers(identifiers: string) {
        await this.dialog().locator('textarea').fill(identifiers);
    }

    exportButton() {
        return this.dialog().locator('button[type="submit"]');
    }

    async export() {
        await this.exportButton().click();
    }
}
