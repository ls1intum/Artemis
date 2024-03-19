import { Page } from 'playwright';
import { BASE_API } from '../../../constants';
import { expect } from '@playwright/test';
import { Fixtures } from '../../../../fixtures/fixtures';

export class FileUploadEditorPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async attachFile(filePath: string) {
        await this.page.locator('#fileUploadInput').setInputFiles(Fixtures.getAbsoluteFilePath(filePath));
    }

    async attachFileExam(filePath: string) {
        await this.page.locator('#fileUploadInput').setInputFiles(filePath);
        await this.page.locator('#file-upload-submit').click();
    }

    async saveAndContinue() {
        // For network requests, Playwright recommends using `waitForResponse` method.
        const responsePromise = this.page.waitForResponse(`${BASE_API}/exercises/*/file-upload-submissions`);
        await this.page.click('#save');
        await responsePromise;
    }

    async submit() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/exercises/*/file-upload-submissions`);
        await this.page.click('#submit');
        return await responsePromise;
    }

    async shouldShowExerciseTitleInHeader(exerciseTitle: string) {
        await expect(this.page.locator('#participation-header').getByText(exerciseTitle)).toBeVisible();
    }

    async shouldShowProblemStatement() {
        await expect(this.page.locator('#problem-statement')).toBeVisible();
    }
}
