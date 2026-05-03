import { Page, expect, Locator } from '@playwright/test';
import { RepositoryPage } from './RepositoryPage';
import { UserCredentials } from '../../../users';

export class ProgrammingExerciseParticipationsPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    getParticipation(participantName: string) {
        return this.page.getByRole('table').getByRole('row').filter({ hasText: participantName });
    }

    getStudentParticipation(user: UserCredentials) {
        // Match against the build plan ID which contains the username uppercased without underscores
        const usernamePattern = user.username.replace(/_/g, '').toUpperCase();
        return this.page
            .getByRole('table')
            .getByRole('row')
            .filter({ hasText: new RegExp(usernamePattern, 'i') });
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

    private async getParticipationCell(participantName: string, columnName: string) {
        let participationRow: Locator = this.getParticipation(participantName);
        return await this.getParticipationCellByLocator(participationRow, columnName);
    }

    async openRepositoryOnNewPage(participantName: string): Promise<RepositoryPage> {
        console.log(`[openRepositoryOnNewPage] Opening repository for participation with participant ${participantName}`);
        const participation = this.getParticipation(participantName);
        await participation.locator('.code-button').click();
        console.log('[openRepositoryOnNewPage] Clicked code button, waiting for popover...');

        // Wait for the popover to appear and the button to be visible
        const openRepoButton = this.page.locator('.open-repository-button');
        await openRepoButton.waitFor({ state: 'visible' });
        console.log('[openRepositoryOnNewPage] Popover visible, getting href...');

        // Get the href from the link and open it in a new page directly
        // This is more reliable than clicking when using Angular's routerLink with target="_blank"
        const href = await openRepoButton.getAttribute('href');
        if (!href) {
            throw new Error('Could not find href on open-repository-button');
        }
        console.log(`[openRepositoryOnNewPage] Found href: ${href}`);

        // Construct absolute URL from the relative href using the page's origin
        const baseUrl = new URL(this.page.url()).origin;
        const absoluteUrl = new URL(href, baseUrl).toString();
        console.log(`[openRepositoryOnNewPage] Navigating to: ${absoluteUrl}`);

        const newPage = await this.page.context().newPage();
        await newPage.goto(absoluteUrl, { waitUntil: 'domcontentloaded' });
        console.log(`[openRepositoryOnNewPage] Navigation complete. New page URL: ${newPage.url()}`);

        return new RepositoryPage(newPage);
    }

    async checkParticipationTeam(participantName: string, teamName: string) {
        const teamCell = await this.getParticipationCell(participantName, 'Team');
        expect(teamCell).not.toBeUndefined();
        await expect(teamCell!.filter({ hasText: teamName })).toBeVisible();
    }

    async checkParticipationStudents(participantName: string, studentUsernames: string[]) {
        const studentsCell = await this.getParticipationCell(participantName, 'Students');
        expect(studentsCell).not.toBeUndefined();
        for (const studentName of studentUsernames) {
            await expect(studentsCell!.filter({ hasText: studentName })).toBeVisible();
        }
    }
}
