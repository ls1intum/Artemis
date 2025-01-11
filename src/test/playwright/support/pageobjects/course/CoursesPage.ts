import { Page } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the courses page (/courses).
 */
export class CoursesPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async openCoursesPage() {
        await this.page.goto('/courses');
        for (let retry = 0; retry < 3; retry++) {
            try {
                await this.page.locator('#test-your-current-courses').waitFor({ state: 'visible', timeout: 3000 });
            } catch (timeoutError) {
                await this.page.goto('/courses');
                continue;
            }
            return;
        }
        throw new Error('Failed to open course page');
    }

    async openCourse(courseId: number) {
        for (let retry = 0; retry < 5; retry++) {
            if (/\/exercises\/\d+/.test(this.page.url()) || /\/exercises/.test(this.page.url())) {
                return;
            } else {
                await this.tryToOpenCourseFromCoursesOverviewPage(courseId);
            }
            await this.page.waitForTimeout(250);
        }
        throw new Error('Could not access course. URL:' + this.page.url());
    }

    async openCourseAndFirstExercise(courseId: number) {
        for (let retry = 0; retry < 5; retry++) {
            if (/\/exercises\/\d+/.test(this.page.url())) {
                return;
            } else if (/\/exercises/.test(this.page.url())) {
                await this.tryToOpenFirstExercise();
            } else {
                await this.tryToOpenCourseFromCoursesOverviewPage(courseId);
            }
            await this.page.waitForTimeout(250);
        }
        throw new Error('Could not access exercise. URL:' + this.page.url());
    }

    async tryToOpenCourseFromCoursesOverviewPage(courseId: number) {
        try {
            if (await this.page.locator(`#course-${courseId}-header`).isVisible({ timeout: 3000 })) {
                await this.page.locator(`#course-${courseId}-header`).click({ timeout: 100 });
            }
        } catch (timeoutError) {}
    }

    async tryToOpenFirstExercise() {
        try {
            if (await this.page.locator('#test-sidebar-card-medium').isVisible({ timeout: 3000 })) {
                await this.page.locator('#test-sidebar-card-medium').click({ timeout: 100 });
            }
        } catch (timeoutError) {}
    }
}
