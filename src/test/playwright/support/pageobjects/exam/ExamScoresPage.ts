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

    async checkGradeDistributionChart() {
        // The grade distribution is rendered by chart.js on a canvas, so the individual axis ticks and
        // bar labels are no longer part of the DOM. Verify that the chart is present and actually painted
        // (the bars draw non-transparent pixels onto the canvas). The underlying numbers are still
        // asserted via checkExamStatistics and checkStudentResults.
        const gradeChart = this.page.locator('jhi-participant-scores-distribution').locator('canvas');
        await expect(gradeChart).toBeVisible({ timeout: 30000 });
        await expect
            .poll(
                () =>
                    gradeChart.evaluate((canvas: HTMLCanvasElement) => {
                        const context = canvas.getContext('2d');
                        if (!context || canvas.width === 0 || canvas.height === 0) {
                            return 0;
                        }
                        const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
                        let paintedPixels = 0;
                        for (let i = 3; i < pixels.length; i += 4) {
                            if (pixels[i] > 0) {
                                paintedPixels++;
                            }
                        }
                        return paintedPixels;
                    }),
                { timeout: 10000 },
            )
            .toBeGreaterThan(0);
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
