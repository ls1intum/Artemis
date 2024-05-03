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

    async openRepository(participationId: number) {
        const participation = this.getParticipation(participationId);
        await participation.locator('a', { hasText: 'Open repository' }).click();
    }

    async checkParticipationBuildPlan(participation: any) {
        await expect(this.getParticipation(participation.id).filter({ hasText: participation.buildPlanId })).toBeVisible();
    }

    async checkParticipationTeam(participationId: number, teamName: string) {
        await expect(this.getParticipation(participationId).filter({ hasText: teamName })).toBeVisible();
    }

    async checkParticationStudents(participationId: number, studentUsernames: string[]) {
        const participation = this.getParticipation(participationId);
        for (const studentName of studentUsernames) {
            await expect(participation.locator('.student-group-item', { hasText: studentName })).toBeVisible();
        }
    }
}
