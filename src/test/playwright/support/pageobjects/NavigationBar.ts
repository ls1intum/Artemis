import { Page } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the navigation bar at the top.
 */
export class NavigationBar {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Opens the consolidated courses page and waits until it is loaded.
     *
     * Under heavy multi-node load the lazy route occasionally fails to resolve, so the dashboard request never fires;
     * a single unbounded waitForResponse would then hang until the whole test times out (observed failure mode: only
     * the footer rendered). Re-navigate with a bounded wait per attempt instead — this recovers from a transient
     * chunk-load miss and only fails the test when the page genuinely never loads.
     */
    async openCourseManagement() {
        for (let attempt = 0; attempt < 3; attempt++) {
            const overviewLoaded = this.page.waitForResponse('**/api/course/courses/for-dashboard*', { timeout: 20000 }).then(
                () => true,
                () => false,
            );
            await this.page.goto('/courses');
            if (await overviewLoaded) {
                await this.page.waitForURL('**/courses');
                return;
            }
        }
        throw new Error('openCourseManagement: consolidated course overview did not load after 3 navigation attempts');
    }

    /**
     * Logs out via the menu at the top.
     */
    async logout() {
        await this.page.locator('#account-menu').click();
        await this.page.locator('[data-testid="logout"]').click();
    }
}
