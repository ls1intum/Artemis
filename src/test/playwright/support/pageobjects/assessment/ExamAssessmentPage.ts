import { BASE_API } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

export class ExamAssessmentPage extends AbstractExerciseAssessmentPage {
    async submitModelingAssessment() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}modeling-submissions/*/result/*/assessment*`);
        await super.submitWithoutInterception();
        return await responsePromise;
    }

    async submitTextAssessment() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}participations/*/results/*/submit-text-assessment`);
        await super.submitWithoutInterception();
        return await responsePromise;
    }
}
