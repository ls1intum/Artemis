import { Page } from '@playwright/test';
import { UserCredentials } from '../../../users';
import { Commands } from '../../../commands';
import { CoursesPage } from '../../course/CoursesPage';
import { CourseOverviewPage } from '../../course/CourseOverviewPage';

export class ProgrammingExerciseOverviewPage {
    private readonly page: Page;
    private readonly courseList: CoursesPage;
    private readonly courseOverview: CourseOverviewPage;

    constructor(page: Page, courseList: CoursesPage, courseOverview: CourseOverviewPage) {
        this.page = page;
        this.courseList = courseList;
        this.courseOverview = courseOverview;
    }

    async getResultScore() {
        const resultScore = this.page.locator('.tab-bar-exercise-details').locator('#result-score');
        await resultScore.waitFor({ state: 'visible' });
        return resultScore;
    }

    async startParticipation(courseId: number, exerciseId: number, credentials: UserCredentials) {
        await Commands.login(this.page, credentials, '/');
        await this.page.waitForURL(/\/courses/);
        await this.courseList.openCourse(courseId!);
        await this.courseOverview.startExercise(exerciseId);
    }

    async openCodeEditor(exerciseId: number) {
        await Commands.reloadUntilFound(this.page, this.page.locator(`#open-exercise-${exerciseId}`));
        await this.courseOverview.openRunningProgrammingExercise(exerciseId);
    }

    async getRepoUrl() {
        const cloneRepoLocator = this.getCodeButton();
        await Commands.reloadUntilFound(this.page, cloneRepoLocator, 4000, 20000);
        await cloneRepoLocator.click();
        await this.page.locator('.popover-body').waitFor({ state: 'visible' });
        return await this.page.locator('.clone-url').innerText();
    }

    getCodeButton() {
        return this.page.locator('.code-button');
    }

    getExerciseDetails() {
        return this.page.locator('.tab-bar-exercise-details');
    }
}
