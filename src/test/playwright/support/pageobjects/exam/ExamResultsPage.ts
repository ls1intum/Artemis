import { Page, expect } from '@playwright/test';
import { Fixtures } from '../../../fixtures/fixtures';
import { getExercise } from '../../utils';

export class ExamResultsPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async checkTextExerciseContent(exerciseID: number, textFixture: string) {
        const textExercise = getExercise(this.page, exerciseID);
        const submissionText = await Fixtures.get(textFixture);
        await expect(textExercise.locator('span', { hasText: submissionText })).toBeVisible();
    }

    async checkAdditionalFeedback(exerciseID: number, points: number, feedback: string) {
        const exercise = getExercise(this.page, exerciseID);
        const feedbackElement = exercise.locator(`#additional-feedback`);
        await expect(feedbackElement.locator('.feedback-points', { hasText: points.toString() })).toBeVisible();
        await expect(feedbackElement.locator('span', { hasText: feedback })).toBeVisible();
    }

    async checkProgrammingExerciseAssessment(exerciseID: number, points: number) {
        const exercise = getExercise(this.page, exerciseID);
        const feedback = exercise.locator('.feedback-item-group');
        await expect(feedback).toHaveText(`${points}P`);
    }

    async checkProgrammingExerciseAssessments(exerciseID: number, resultType: string, count: number) {
        const exercise = getExercise(this.page, exerciseID);
        const results = exercise.locator('.feedback-item-group', { hasText: resultType });
        await expect(results.getByText(`(${count})`)).toBeVisible();
    }

    async checkProgrammingExerciseTasks(exerciseID: number, taskFeedbacks: ProgrammingExerciseTaskStatus[]) {
        const exercise = getExercise(this.page, exerciseID);
        const tasks = exercise.locator('.stepwizard .stepwizard-step');
        for (let taskIndex = 0; taskIndex < taskFeedbacks.length; taskIndex++) {
            const taskElement = tasks.nth(taskIndex);
            switch (taskFeedbacks[taskIndex]) {
                case ProgrammingExerciseTaskStatus.PENDING:
                    await expect(taskElement.locator('.stepwizard-step--no-result')).toBeVisible();
                    break;
                case ProgrammingExerciseTaskStatus.SUCCESS:
                    await expect(taskElement.locator('.stepwizard-step--success')).toBeVisible();
                    break;
                case ProgrammingExerciseTaskStatus.FAILURE:
                    await expect(taskElement.locator('.stepwizard-step--failed')).toBeVisible();
                    break;
            }
        }
    }

    async checkModellingExerciseAssessment(exerciseID: number, element: string, feedback: string, points: number) {
        const exercise = getExercise(this.page, exerciseID);
        const componentFeedbacks = exercise.locator('#component-feedback-table');
        const assessmentRow = componentFeedbacks.locator('tr', { hasText: element });
        await expect(assessmentRow).toBeVisible();
        await expect(assessmentRow.getByText(`Feedback: ${feedback}`)).toBeVisible();
        await expect(assessmentRow.getByText(`${points}`)).toBeVisible();
    }
}

export enum ProgrammingExerciseTaskStatus {
    PENDING = 'pending',
    SUCCESS = 'success',
    FAILURE = 'failure',
}
