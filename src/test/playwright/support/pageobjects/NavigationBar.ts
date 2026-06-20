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
     * Opens the course management page via the menu at the top and waits until it is loaded.
     *
     * Under heavy multi-node load the lazy course-management chunk / route occasionally fails to resolve, so the
     * course-management-overview request never fires; a single unbounded waitForResponse would then hang until the
     * whole test times out (observed failure mode: only the footer rendered). Re-navigate (a fresh goto re-requests the
     * chunk) with a bounded wait per attempt instead — this recovers from a transient chunk-load miss far faster than
     * the original single 60s hang, and only fails the test when the page genuinely never loads.
     */
    async openCourseManagement() {
        for (let attempt = 0; attempt < 3; attempt++) {
            const overviewLoaded = this.page.waitForResponse(`api/course/courses/course-management-overview*`, { timeout: 20000 }).then(
                () => true,
                () => false,
            );
            await this.page.goto('/course-management');
            if (await overviewLoaded) {
                await this.page.waitForURL('**/course-management**');
                return;
            }
        }
        throw new Error('openCourseManagement: course-management overview did not load after 3 navigation attempts');
    }

    /**
     * Logs out via the menu at the top.
     */
    async logout() {
        await this.page.locator('#account-menu').click();
        await this.page.locator('#logout').click();
    }
}
