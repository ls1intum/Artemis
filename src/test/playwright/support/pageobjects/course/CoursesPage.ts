import { Page } from '@playwright/test';
import { retry } from '../../utils';

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
        await retry(
            this.page,
            async () => {
                await this.page.locator('#test-your-current-courses').waitFor({ state: 'visible', timeout: 3000 });
            },
            '/courses',
            'Could not open course page ' + this.page.url(),
        );
    }

    async openCourse(courseId: number) {
        await retry(
            this.page,
            async () => {
                if (/\/exercises\/\d+/.test(this.page.url()) || /\/exercises/.test(this.page.url())) {
                    return;
                } else {
                    if (await this.page.locator(`#course-${courseId}-header`).isVisible({ timeout: 3000 })) {
                        await this.page.locator(`#course-${courseId}-header`).click({ timeout: 1000 });
                    }
                }
            },
            `/courses/${courseId}`,
            'Could not access course. URL:' + this.page.url(),
        );
    }

    async openSpecificExerciseDirectly(url: string) {
        await this.page.goto(url);
        await this.page.waitForURL(url, { timeout: 3000 });
        await retry(
            this.page,
            async () => {
                console.log(this);
                await this.page.waitForSelector('#test-sidebar-card-medium', { state: 'visible', timeout: 3000 });
            },
            url,
            'Could open exercise directly. URL:' + this.page.url(),
        );
    }
}
