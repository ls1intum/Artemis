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

    /**
     * Checks the grade distribution chart of the exam scores page.
     * @param expectedBucketCount one bucket per grade step of the exam's grading scale
     */
    async checkGradeDistributionChart(expectedBucketCount: number) {
        // The distribution is drawn as SVG, so its content is real DOM rather than painted pixels.
        // The chart draws one bar per grade step and repeats the same buckets in the data table it
        // renders for assistive technology, so both have to reach the expected count. Asserting the
        // exact count also rules out a half-rendered chart passing on a partial bucket set.
        const chart = this.page.locator('jhi-participant-scores-distribution tum-ui-bar-chart');
        await expect(chart).toBeVisible({ timeout: 30000 });

        const bars = chart.locator('rect.tum-ui-bar-chart-bar');
        await expect(bars).toHaveCount(expectedBucketCount, { timeout: 30000 });
        const rows = chart.locator('tum-ui-chart-data-table tbody tr');
        await expect(rows).toHaveCount(expectedBucketCount, { timeout: 10000 });

        await expect
            .poll(() => chart.locator('text.tum-ui-bar-chart-data-label').evaluateAll((labels) => labels.some((label) => !(label.textContent ?? '').trim().startsWith('0 '))), {
                timeout: 10000,
            })
            .toBe(true);
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
