import { Page, expect } from '@playwright/test';
import { StudentResult } from 'app/exam/exam-scores/exam-score-dtos.model';

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
        const header = this.page.locator('th', { hasText: examStat.stat });
        const row = this.page.locator('tr', { has: header });
        await expect(row.locator('td').nth(0).getByText(examStat.passed)).toBeVisible();
        await expect(row.locator('td').nth(1).getByText(examStat.submitted)).toBeVisible();
        await expect(row.locator('td').nth(2).getByText(examStat.total)).toBeVisible();
    }

    async checkGradeDistributionChart(gradeDistribution: any[]) {
        const gradeChart = this.page.locator('jhi-participant-scores-distribution').locator('.bar-chart');
        const chartTicks = gradeChart.locator('g').first().locator('.tick');
        const chartLabels = gradeChart.locator('> g:nth-child(3) > g:nth-child(2)');

        for (let index = 0; index < gradeDistribution.length; index++) {
            const grade = gradeDistribution[index];
            const chartTick = chartTicks.nth(index);
            const chartLabel = chartLabels.locator('g').nth(index);

            await expect(chartTick.locator('text', { hasText: grade.name })).toBeVisible();
            await expect(chartLabel.locator('text', { hasText: grade.count })).toBeVisible();
        }
    }

    async checkStudentResults(studentResults: StudentResult[]) {
        for (const studentResult of studentResults) {
            await this.checkStudentResult(studentResult);
        }
    }

    private async checkStudentResult(studentResult: StudentResult) {
        const studentResultRow = this.page.locator('tr', { hasText: studentResult.login });
        await expect(studentResultRow.locator('td').nth(6).getByText(Math.floor(studentResult.overallPointsAchieved!).toString())).toBeVisible();
        await expect(studentResultRow.locator('td').nth(7).getByText(Math.floor(studentResult.overallScoreAchieved!).toString())).toBeVisible();
        await expect(studentResultRow.locator('td').nth(8).getByText(studentResult.overallGrade!)).toBeVisible();
    }
}
