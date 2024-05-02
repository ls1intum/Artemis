import { Page } from '@playwright/test';

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
}
