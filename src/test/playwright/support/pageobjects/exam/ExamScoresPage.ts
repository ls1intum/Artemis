import { Page, expect } from '@playwright/test';
import { StudentResult } from 'app/exam/manage/exam-scores/exam-score-dtos.model';

export class ExamScoresPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async checkExamStatistics(examStatistics: any[]) {
        for (const examStat of examStatistics) {
            await this.checkExamStat(examStat);
        }
    }

    private async checkExamStat(examStat: any) {
        // Use .first() to scope to the exam-level statistics row and avoid matching
        // duplicate headers in the exercise statistics tables further down the page.
        const header = this.page.locator('th', { hasText: examStat.stat }).first();
        const row = this.page.locator('tr', { has: header }).first();
        await expect(row).toBeVisible({ timeout: 15000 });
        await expect(row.locator('td').nth(0).getByText(examStat.passed)).toBeVisible({ timeout: 10000 });
        await expect(row.locator('td').nth(1).getByText(examStat.submitted)).toBeVisible({ timeout: 10000 });
        await expect(row.locator('td').nth(2).getByText(examStat.total)).toBeVisible({ timeout: 10000 });
    }

    async checkGradeDistributionChart(gradeDistribution: any[]) {
        const gradeChart = this.page.locator('jhi-participant-scores-distribution').locator('.bar-chart');
        await expect(gradeChart).toBeVisible({ timeout: 30000 });
        const chartTicks = gradeChart.locator('g').first().locator('.tick');
        const chartLabels = gradeChart.locator('> g:nth-child(3) > g:nth-child(2)');

        for (let index = 0; index < gradeDistribution.length; index++) {
            const grade = gradeDistribution[index];
            const chartTick = chartTicks.nth(index);
            const chartLabel = chartLabels.locator('g').nth(index);

            await expect(chartTick.locator('text', { hasText: grade.name })).toBeVisible({ timeout: 10000 });
            await expect(chartLabel.locator('text', { hasText: grade.count })).toBeVisible({ timeout: 10000 });
        }
    }

    async checkStudentResults(studentResults: StudentResult[]) {
        for (const studentResult of studentResults) {
            await this.checkStudentResult(studentResult);
        }
    }

    private async checkStudentResult(studentResult: StudentResult) {
        const { overallPointsAchieved, overallScoreAchieved, overallGrade } = studentResult;
        if (overallPointsAchieved === undefined || overallScoreAchieved === undefined || overallGrade === undefined) {
            throw new Error(
                `StudentResult for ${studentResult.login} is missing required fields: ` +
                    `overallPointsAchieved=${overallPointsAchieved}, overallScoreAchieved=${overallScoreAchieved}, overallGrade=${overallGrade}`,
            );
        }
        const studentResultRow = this.page.locator('tr', { hasText: studentResult.login });
        await expect(studentResultRow).toBeVisible({ timeout: 15000 });
        await expect(studentResultRow.locator('td').nth(6).getByText(Math.floor(overallPointsAchieved).toString())).toBeVisible({ timeout: 10000 });
        await expect(studentResultRow.locator('td').nth(7).getByText(Math.floor(overallScoreAchieved).toString())).toBeVisible({ timeout: 10000 });
        await expect(studentResultRow.locator('td').nth(8).getByText(overallGrade)).toBeVisible({ timeout: 10000 });
    }
}
