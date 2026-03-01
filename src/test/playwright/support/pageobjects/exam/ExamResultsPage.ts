import { Page, expect } from '@playwright/test';
import { Fixtures } from '../../../fixtures/fixtures';
import { getExercise } from '../../utils';

export class ExamResultsPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async checkGradeSummary(gradeSummary: any) {
        const examSummary = this.page.locator('#exam-summary-result-overview .exam-points-summary-container');
        for (const exercise of gradeSummary.studentExam.exercises) {
            const exerciseGroup = exercise.exerciseGroup;
            const exerciseRow = examSummary.locator('tr', { hasText: exerciseGroup.title });

            const exerciseResult = gradeSummary.studentResult.exerciseGroupIdToExerciseResult[exerciseGroup.id];
            const achievedPoints = Math.floor(exerciseResult.achievedPoints).toString();
            const achievablePoints = Math.floor(exerciseResult.maxScore).toString();
            const achievedPercentage = exerciseResult.achievedScore.toString();

            await expect(exerciseRow.locator('td').nth(1).getByText(achievedPoints)).toBeVisible();
            await expect(exerciseRow.locator('td').nth(2).getByText(achievablePoints)).toBeVisible();
            await expect(exerciseRow.locator('td').nth(3).getByText(`${achievedPercentage} %`)).toBeVisible();
        }
    }

    async checkTextExerciseContent(exerciseId: number, textFixture: string) {
        const textExercise = getExercise(this.page, exerciseId);
        const submissionText = await Fixtures.get(textFixture);
        await expect(textExercise.locator('span', { hasText: submissionText })).toBeVisible();
    }

    async checkAdditionalFeedback(exerciseId: number, points: number, feedback: string) {
        const exercise = getExercise(this.page, exerciseId);
        const feedbackElement = exercise.locator(`#additional-feedback`);
        await expect(feedbackElement.locator('.unified-feedback-points', { hasText: points.toString() })).toBeVisible();
        await expect(feedbackElement.locator('.unified-feedback-text', { hasText: feedback })).toBeVisible();
    }

    async checkProgrammingExerciseAssessments(exerciseId: number, resultType: string, count: number) {
        const exercise = getExercise(this.page, exerciseId);
        const results = exercise.locator('.feedback-item-group', { hasText: resultType });
        await expect(results.getByText(`(${count})`)).toBeVisible();
    }

    async checkProgrammingExerciseTasks(exerciseId: number, taskFeedbacks: ProgrammingExerciseTaskStatus[]) {
        const exercise = getExercise(this.page, exerciseId);
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

    async checkQuizExerciseScore(exerciseId: number, score: number, maxScore: number) {
        const exercise = getExercise(this.page, exerciseId);
        await expect(exercise.locator('.question-score').getByText(`${score}/${maxScore}`)).toBeVisible();
    }

    async checkQuizExerciseAnswers(exerciseId: number, studentAnswers: boolean[], correctAnswers: boolean[]) {
        const exercise = getExercise(this.page, exerciseId);

        for (let i = 0; i < studentAnswers.length; i++) {
            const selectedAnswer = exercise.locator('.selection').nth(i + 1);
            if (studentAnswers[i]) {
                await expect(selectedAnswer.locator('.fa-square-check')).toBeVisible();
            }
            if (!studentAnswers[i]) {
                await expect(selectedAnswer.locator('.fa-square')).toBeVisible();
            }

            const solution = exercise.locator('.solution').nth(i + 1);
            await expect(solution).toHaveText(correctAnswers[i] ? 'Correct' : 'Wrong');
        }
    }

    async checkModellingExerciseAssessment(exerciseId: number, element: string, feedback: string, points: number) {
        const exercise = getExercise(this.page, exerciseId);
        const componentFeedbacks = exercise.locator('#component-feedback-table');
        const feedbackElement = componentFeedbacks.locator('.unified-feedback', { hasText: element });
        await expect(feedbackElement).toBeVisible();
        await expect(feedbackElement.locator('.unified-feedback-title', { hasText: feedback })).toBeVisible();
        await expect(feedbackElement.locator('.unified-feedback-points', { hasText: points.toString() })).toBeVisible();
    }
}

export enum ProgrammingExerciseTaskStatus {
    PENDING = 'pending',
    SUCCESS = 'success',
    FAILURE = 'failure',
}
