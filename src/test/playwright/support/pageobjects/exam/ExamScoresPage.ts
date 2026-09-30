import fs from 'fs';
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
        const chart = this.page.locator('jhi-participant-scores-distribution tumaet-ui-bar-chart');
        await expect(chart).toBeVisible({ timeout: 30000 });

        const bars = chart.locator('rect.tumaet-ui-bar-chart-bar');
        await expect(bars).toHaveCount(expectedBucketCount, { timeout: 30000 });
        const rows = chart.locator('tumaet-ui-chart-data-table tbody tr');
        await expect(rows).toHaveCount(expectedBucketCount, { timeout: 10000 });

        await expect
            .poll(() => chart.locator('text.tumaet-ui-bar-chart-data-label').evaluateAll((labels) => labels.some((label) => !(label.textContent ?? '').trim().startsWith('0 '))), {
                timeout: 10000,
            })
            .toBe(true);
    }

    /**
     * Exports the results of the exam as a CSV file and returns the file's content.
     */
    async exportResultsAsCsv(): Promise<string> {
        await this.page.getByRole('button', { name: 'Export', exact: true }).click();
        const dialog = this.page.getByRole('dialog');
        await dialog.getByRole('tab', { name: 'CSV' }).click();
        const download = this.page.waitForEvent('download');
        await dialog.locator('#finish-button').click();
        return fs.readFileSync(await (await download).path(), 'utf-8');
    }

    /**
     * Checks that an exported CSV file holds a line per student with the student's login, points and grade.
     */
    checkExportedResults(csv: string, studentResults: StudentResult[]) {
        const lines = csv.split(/\r?\n/).filter(Boolean);
        for (const studentResult of studentResults) {
            const line = lines.find((candidate) => candidate.includes(studentResult.login!));
            expect(line, `the export has a line for ${studentResult.login}:\n${csv}`).toBeDefined();
            expect(line, `points of ${studentResult.login}`).toContain(Math.floor(studentResult.overallPointsAchieved!).toString());
            expect(line, `grade of ${studentResult.login}`).toContain(studentResult.overallGrade!);
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
        const studentResultRow = this.page.locator(`[data-testid="student-result-row"][data-login="${studentResult.login}"]`);
        await expect(studentResultRow).toBeVisible({ timeout: 15000 });
        await expect(studentResultRow.getByTestId('overall-points').getByText(Math.floor(overallPointsAchieved).toString())).toBeVisible({ timeout: 10000 });
        await expect(studentResultRow.getByTestId('overall-score').getByText(Math.floor(overallScoreAchieved).toString())).toBeVisible({ timeout: 10000 });
        await expect(studentResultRow.getByTestId('overall-grade').getByText(overallGrade)).toBeVisible({ timeout: 10000 });
    }
}
