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
}
