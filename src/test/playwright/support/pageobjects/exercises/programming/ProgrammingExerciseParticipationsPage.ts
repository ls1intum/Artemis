import { Page, expect } from '@playwright/test';

export class ProgrammingExerciseParticipationsPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    getParticipation(participationId: number) {
        return this.page
            .getByRole('table')
            .getByRole('row')
            .filter({ hasText: `${participationId}` });
    }

    private async getParticipationCell(participationId: number, columnName: string) {
        const headerCells = this.page.getByRole('table').getByRole('columnheader');
        const numberOfColumns = await headerCells.count();
        const participationRow = this.getParticipation(participationId);

        for (let columnIndex = 0; columnIndex < numberOfColumns; columnIndex++) {
            const textContent = await headerCells.nth(columnIndex).textContent();
            if (textContent?.includes(columnName)) {
                return participationRow.getByRole('cell').nth(columnIndex);
            }
        }
    }

    async openRepository(participationId: number) {
        const participation = this.getParticipation(participationId);
        await participation.locator('a', { hasText: 'Open repository' }).click();
    }

    async checkParticipationBuildPlan(participation: any) {
        const buildPlanIdCell = await this.getParticipationCell(participation.id, 'Build Plan Id');
        expect(buildPlanIdCell).not.toBeUndefined();
        await expect(buildPlanIdCell!.filter({ hasText: participation.buildPlanId })).toBeVisible();
    }

    async checkParticipationTeam(participationId: number, teamName: string) {
        const teamCell = await this.getParticipationCell(participationId, 'Team');
        expect(teamCell).not.toBeUndefined();
        await expect(teamCell!.filter({ hasText: teamName })).toBeVisible();
    }

    async checkParticipationStudents(participationId: number, studentUsernames: string[]) {
        const studentsCell = await this.getParticipationCell(participationId, 'Students');
        expect(studentsCell).not.toBeUndefined();
        for (const studentName of studentUsernames) {
            await expect(studentsCell!.filter({ hasText: studentName })).toBeVisible();
        }
    }
}
