import { Page } from '@playwright/test';
import { BASE_API } from '../../../constants';
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
        const responsePromise = this.page.waitForResponse(`${BASE_API}/fileupload/exercises/*/file-upload-submissions`);
        await this.page.getByTestId('exam-file-upload-area').filter({ visible: true }).getByTestId('exam-file-upload-input').setInputFiles(filePath);
        await responsePromise;
    }

    async saveAndContinue() {
        // For network requests, Playwright recommends using `waitForResponse` method.
        const responsePromise = this.page.waitForResponse(`${BASE_API}/fileupload/exercises/*/file-upload-submissions`);
        await this.page.click('#save');
        await responsePromise;
    }
}
