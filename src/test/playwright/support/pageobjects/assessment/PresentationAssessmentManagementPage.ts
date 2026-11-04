import { Locator, Page, expect } from '@playwright/test';
import dayjs from 'dayjs';
import { Commands } from '../../commands';
import { fillDateTimePicker } from '../../utils';
import { PresentationGradingMethod } from '../../constants';

/** The date-only field of the TUM AET UI date picker shows the date as DD.MM.YYYY. */
const PRESENTATION_DATE_FORMAT = 'DD.MM.YYYY';

/**
 * Page object for the presentation assessment management of a course.
 */
export class PresentationAssessmentManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /** Opens the presentation management of the course and waits until its overview is shown. */
    async goto(courseId: number) {
        await Commands.gotoAndEnsureRendered(this.page, `/course-management/${courseId}/presentations`);
        await expect(this.page.getByTestId('presentation-master-detail')).toBeVisible();
    }

    /** Opens a presentation from the sidebar, the way a user selects it. */
    async openPresentation(title: string) {
        await this.page.getByRole('link', { name: title }).click();
        await expect(this.page.getByTestId('presentation-instances-table')).toBeVisible();
    }

    /**
     * Creates a presentation through the steps of the create dialog: its details, its grading method and, unless graded by pass/fail,
     * the configuration of the method, which is the max points of a score or the criteria of a rubric.
     */
    async createPresentation(title: string, gradingMethod: PresentationGradingMethod, configuration: { maxPoints?: number; criteria?: { name: string; weight: number }[] } = {}) {
        await this.page.getByTestId('create-presentation-button').click();
        await this.page.locator('#presentation-assessment-title').fill(title);
        await this.page.getByTestId('continue-presentation-button').click();
        await this.page.getByTestId(`grading-method-${gradingMethod}`).click();
        if (gradingMethod !== PresentationGradingMethod.PASS_FAIL) {
            await this.page.getByTestId('continue-presentation-button').click();
        }
        if (configuration.maxPoints !== undefined) {
            await this.page.locator('#presentation-assessment-max-points').fill(String(configuration.maxPoints));
        }
        for (const [index, criterion] of (configuration.criteria ?? []).entries()) {
            await this.page.getByTestId(index === 0 ? 'rubric-add-first-criterion' : 'rubric-add-criterion').click();
            await this.page.locator(`#rubric-criterion-name-${index}`).fill(criterion.name);
            await this.page.locator(`#rubric-criterion-weight-${index}`).fill(String(criterion.weight));
        }
        const created = this.page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/presentation-assessments'));
        await this.page.getByTestId('save-presentation-button').click();
        expect((await created).status()).toBe(201);
    }

    /** Assigns a student to the open presentation and records the grade in one go. */
    async assignAndGrade(studentLogin: string, date: dayjs.Dayjs, points: number) {
        await this.assign(studentLogin, date, () => this.page.locator('#presentation-instance-result').fill(String(points)));
    }

    /** Assigns a student to a presentation graded by pass/fail and records whether the student passed. */
    async assignAndGradeByPassFail(studentLogin: string, date: dayjs.Dayjs, passed: boolean) {
        await this.assign(studentLogin, date, () => this.page.getByTestId(passed ? 'presentation-instance-passed' : 'presentation-instance-failed').click());
    }

    /** Assigns a student to a presentation graded by rubric and rates every criterion at the given level position, counting from 0 at the lowest level. */
    async assignAndGradeByRubric(studentLogin: string, date: dayjs.Dayjs, levelPositions: number[]) {
        await this.assign(studentLogin, date, async () => {
            for (const [criterionIndex, position] of levelPositions.entries()) {
                await this.page.getByTestId(`rubric-grading-level-${criterionIndex}-${position}`).click();
            }
        });
    }

    async changeGrade(studentLogin: string, points: number) {
        await this.instanceRow(studentLogin).getByTestId('edit-instance-button').click();
        await this.page.locator('#presentation-instance-result').fill(String(points));
        const saved = this.page.waitForResponse((response) => response.request().method() === 'PUT' && response.url().includes('/instances/'));
        await this.page.getByTestId('save-instance-button').click();
        expect((await saved).ok()).toBe(true);
    }

    async deleteInstance(studentLogin: string) {
        await this.instanceRow(studentLogin).getByTestId('delete-instance-button').click();
        const deleted = this.page.waitForResponse((response) => response.request().method() === 'DELETE' && response.url().includes('/instances/'));
        await this.page.getByTestId('delete-dialog-confirm-button').click();
        expect((await deleted).ok()).toBe(true);
    }

    /** Deletes the open presentation; the dialog asks to type its title first. */
    async deletePresentation(title: string) {
        await this.page.getByTestId('edit-presentation-button').click();
        await this.page.getByTestId('delete-presentation-button').click();
        await this.page.locator('#confirm-entity-name').fill(title);
        const deleted = this.page.waitForResponse((response) => response.request().method() === 'DELETE' && response.url().includes('/presentation-assessments/'));
        await this.page.getByTestId('delete-dialog-confirm-button').click();
        expect((await deleted).ok()).toBe(true);
    }

    /** The row of a student in the table of the open presentation. */
    instanceRow(studentLogin: string): Locator {
        return this.page.getByTestId('presentation-instances-table').locator('tbody tr').filter({ hasText: studentLogin });
    }

    private async selectPresenter(studentLogin: string) {
        await this.page.getByTestId('presenter-search').locator('input').fill(studentLogin);
        await this.page.getByRole('option', { name: studentLogin }).click();
    }

    private async assign(studentLogin: string, date: dayjs.Dayjs, enterResult: () => Promise<unknown>) {
        await this.page.getByTestId('create-instance-button').click();
        await fillDateTimePicker(this.page.locator('#presentation-instance-date'), date, PRESENTATION_DATE_FORMAT);
        await enterResult();
        await this.selectPresenter(studentLogin);
        const saved = this.page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/instances'));
        await this.page.getByTestId('save-instance-button').click();
        expect((await saved).ok()).toBe(true);
    }
}
