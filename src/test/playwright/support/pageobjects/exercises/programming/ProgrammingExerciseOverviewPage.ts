import { expect, Page } from '@playwright/test';
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

    async checkResultScore(expectedResult: string) {
        const resultScore = this.page.locator('#exercise-headers-information').locator('#result-score');
        await resultScore.waitFor({ state: 'visible' });
        await expect(resultScore.getByText(expectedResult)).toBeVisible();
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

    async openCloneMenu(cloneMethod: GitCloneMethod) {
        const gitCloneMethodSelector = {
            [GitCloneMethod.https]: '#useHTTPSButton',
            [GitCloneMethod.httpsWithToken]: '#useHTTPSWithTokenButton',
            [GitCloneMethod.ssh]: '#useSSHButton',
        };

        const codeButtonLocator = this.getCodeButton();
        await Commands.reloadUntilFound(this.page, codeButtonLocator, 4000, 20000);
        await codeButtonLocator.click();
        await this.page.locator('.popover-body').waitFor({ state: 'visible' });
        await this.page.locator('.https-or-ssh-button').click();
        await this.page.locator(gitCloneMethodSelector[cloneMethod]).click();
    }

    async getCloneUrl() {
        return await this.page.locator('.clone-url').innerText();
    }

    async copyCloneUrl() {
        await this.page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
        await this.getCloneUrlButton().click();
        return await this.page.evaluate(async () => {
            return await navigator.clipboard.readText();
        });
    }

    getCodeButton() {
        return this.page.locator('.code-button');
    }

    getExerciseDetails() {
        return this.page.locator('#course-exercise-details');
    }

    getCloneUrlButton() {
        return this.page.getByTestId('copyRepoUrlButton');
    }
}

export enum GitCloneMethod {
    https = 'https',
    httpsWithToken = 'https with token',
    ssh = 'ssh',
}
