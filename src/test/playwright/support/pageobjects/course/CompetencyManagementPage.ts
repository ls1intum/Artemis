import { Page } from '@playwright/test';

export class CompetencyManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async goto(courseId: number) {
        // Opportunistically wait for the initial competency fetch so subsequent steps are not
        // racing the page load. The wait is bounded (30s) and `.catch`ed so it never blocks
        // indefinitely — when the response truly stalls, the caller's own auto-waiting
        // locator assertion will surface the real failure. On timeout we also reload once,
        // which reliably recovers from the multi-node case where the API request never fires
        // because Angular's lazy chunk silently failed but `#account-menu` still attached
        // (so the page-fixture reload didn't trigger).
        //
        // The `page.goto` fixture wrapper (see `support/fixtures.ts`) handles the case where
        // Angular fails to bootstrap the route component on the first navigation by
        // reloading once when `#account-menu` is missing.
        const responseMatcher = (resp: { url(): string; status(): number }) => resp.url().includes(`/api/atlas/courses/${courseId}/course-competencies`) && resp.status() < 400;
        const responsePromise = this.page.waitForResponse(responseMatcher, { timeout: 30000 }).catch(() => undefined);
        await this.page.goto(`/course-management/${courseId}/competency-management`);
        const firstResponse = await responsePromise;
        if (!firstResponse) {
            // The lazy chunk attached but the route component never issued the fetch — reload
            // once with a fresh listener.
            const retryResponse = this.page.waitForResponse(responseMatcher, { timeout: 30000 }).catch(() => undefined);
            await this.page.reload();
            await this.page.waitForLoadState('load');
            await retryResponse;
        }
        const closeButton = this.page.locator('#close-button');
        if (await closeButton.isVisible()) {
            await closeButton.click();
        }
    }
}
