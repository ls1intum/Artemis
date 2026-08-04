import { expect } from '@playwright/test';
import { BASE_API } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

export class ExamAssessmentPage extends AbstractExerciseAssessmentPage {
    async submitModelingAssessment() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/modeling/modeling-submissions/*/results/*/assessment*`);
        await super.submitWithDialogHandler();
        return await responsePromise;
    }

    async submitTextAssessment() {
        // The server occasionally returns 500 on multi-node setups when Hazelcast L2-cache invalidation
        // for Result.feedbacks (ordered list) lags between nodes. Retry up to 2 times on 500 with a brief
        // delay so the cache settles. Status >=400 triggers retry; 200 returns immediately.
        for (let attempt = 0; attempt < 3; attempt++) {
            const responsePromise = this.page.waitForResponse(`${BASE_API}/text/participations/*/results/*/submit-text-assessment`);
            await super.submitWithoutInterception();
            const response = await responsePromise;
            if (response.status() < 400 || attempt === 2) {
                return response;
            }
            await this.page.waitForTimeout(1500);
        }
        // Unreachable, retained for type completeness.
        throw new Error('submitTextAssessment exhausted retries');
    }

    /**
     * Saves the assessment as a draft without submitting it and returns the save response.
     */
    async saveTextAssessment() {
        // Wait for the button to be enabled rather than racing it: saving is only allowed once the feedback the test
        // just entered has been validated, and clicking too early produces no request at all.
        const saveButton = this.page.locator('#save');
        await saveButton.waitFor({ state: 'visible' });
        await expect(saveButton).toBeEnabled({ timeout: 10000 });
        const responsePromise = this.page.waitForResponse(
            (response) => /\/participations\/\d+\/results\/\d+\/text-assessment$/.test(new URL(response.url()).pathname) && response.request().method() === 'PUT',
        );
        await saveButton.click();
        return await responsePromise;
    }

    /**
     * The correction round the editor believes it is in, taken from the URL, which is the only place it is carried.
     */
    correctionRoundInUrl(): string | null {
        return new URL(this.page.url()).searchParams.get('correction-round');
    }

    async expectSubmitEnabled() {
        await expect(this.page.locator('#submit')).toBeEnabled({ timeout: 10000 });
    }
}
