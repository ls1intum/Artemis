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
        const responsePromise = this.page.waitForResponse(`${BASE_API}/text/participations/*/results/*/text-assessment`);
        await this.page.locator('#save').click();
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
