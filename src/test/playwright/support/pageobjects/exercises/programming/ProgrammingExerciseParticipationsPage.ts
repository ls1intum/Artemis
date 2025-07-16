import { Page, expect, Locator } from '@playwright/test';
import { RepositoryPage } from './RepositoryPage';
import { UserCredentials } from '../../../users';

export class ProgrammingExerciseParticipationsPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    getParticipation(participationId: number) {
        return this.getParticipationByText(participationId.toString());
    }

    getStudentParticipation(user: UserCredentials) {
        return this.getParticipationByText(user.username);
    }

    private getParticipationByText(text: string) {
        return this.page
            .getByRole('table')
            .getByRole('row')
            .filter({ hasText: `${text}` });
    }

    public async openStudentParticipationSubmissions(user: UserCredentials) {
        let row = this.getStudentParticipation(user);
        await row.waitFor({ state: 'visible' });
        let id = await this.getParticipationCellByLocator(row, 'Submissions');
        await id!.locator('a').click();
        await this.page.waitForURL('**/participations/*/submissions');
    }

    public async getParticipationCellByLocator(participationRow: Locator, columnName: string) {
        const headerCells = this.page.getByRole('table').getByRole('columnheader');
        const numberOfColumns = await headerCells.count();
        for (let columnIndex = 0; columnIndex < numberOfColumns; columnIndex++) {
            const textContent = await headerCells.nth(columnIndex).textContent();
            if (textContent?.includes(columnName)) {
                return participationRow.getByRole('cell').nth(columnIndex);
            }
        }
    }

    private async getParticipationCell(participationId: number, columnName: string) {
        let participationRow: Locator = this.getParticipation(participationId);
        return await this.getParticipationCellByLocator(participationRow, columnName);
    }

    async openRepositoryOnNewPage(participationId: number): Promise<RepositoryPage> {
        const participation = this.getParticipation(participationId);
        await participation.locator('.code-button').click();
        // The link opens the repository in a new tab, so we need to wait for the new page to be created.
        const pagePromise = this.page.context().waitForEvent('page');
        await this.page.locator('.open-repository-button').click();
        return new RepositoryPage(await pagePromise);
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
