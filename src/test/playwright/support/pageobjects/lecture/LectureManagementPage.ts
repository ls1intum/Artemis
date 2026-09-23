import { Page } from '@playwright/test';
import dayjs from 'dayjs';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { expect } from '@playwright/test';
import { BASE_API } from '../../constants';
import { fillDateTimePicker, setMonacoEditorContentByLocator } from '../../utils';
import { Commands } from '../../commands';

/**
 * A class which encapsulates UI selectors and actions for the Lecture Management Page.
 */
export class LectureManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Clicks the button to create a new lecture.
     */
    async clickCreateLecture() {
        await this.page.click('#jh-create-entity');
    }

    /**
     * Deletes a specified lecture by its identifier.
     * @param lecture - The lecture to be deleted.
     * @returns A promise that resolves with the response of the delete action.
     */
    async deleteLecture(lecture: Lecture) {
        const lectureRow = this.getLecture(lecture.id!);
        await lectureRow.waitFor({ state: 'visible', timeout: 30_000 });
        await lectureRow.locator('[data-testid="delete-lecture"]').click();
        const deleteButton = this.page.getByTestId('delete-dialog-confirm-button');
        await expect(deleteButton).toBeDisabled();
        await this.page.fill('#confirm-entity-name', lecture.title!);
        const responsePromise = this.page.waitForResponse(`${BASE_API}/lecture/lectures/*`);
        await deleteButton.click();
        return await responsePromise;
    }

    /**
     * Retrieves a locator for the container holding all lectures.
     * @returns A Playwright locator for the lectures container.
     */
    getLectures() {
        return this.page.locator('#lectures');
    }

    /**
     * Retrieves a locator for a specific lecture by its identifier.
     * @param lectureId - The identifier of the lecture.
     * @returns A Playwright locator for the specified lecture.
     */
    getLecture(lectureId: number) {
        return this.page.locator(`#lecture-${lectureId}`);
    }

    /**
     * Retrieves a locator for the lecture preview container.
     * @returns A Playwright locator for the lecture preview container.
     */
    getLectureContainer() {
        return this.page.locator('#lecture-preview');
    }

    /**
     * Navigates to the units page of a specified lecture by its identifier.
     *
     * Navigates directly via URL rather than clicking through the lectures list.
     * Under heavy parallel multi-node load the lectures-list page occasionally fails
     * to fully hydrate (only the app shell renders, the lectures table never appears),
     * which made the previous click-through approach race the SPA bootstrap. Direct
     * URL navigation removes the dependency on the list page entirely; we additionally
     * wait for the unit-creation card to confirm the target page is hydrated before
     * the caller starts interacting with it.
     */
    async openUnitsPage(lectureId: number) {
        const courseIdMatch = this.page.url().match(/\/course-management\/(\d+)/);
        if (courseIdMatch) {
            await Commands.gotoAndEnsureRendered(this.page, `/course-management/${courseIdMatch[1]}/lectures/${lectureId}/unit-management`);
        } else {
            const lectureRow = this.getLecture(lectureId);
            await lectureRow.waitFor({ state: 'visible', timeout: 30_000 });
            await lectureRow.locator('#units').click();
        }
        await this.getUnitCreationCard().waitFor({ state: 'visible', timeout: 30_000 });
    }

    /**
     * Opens the creation form for a file/video content unit, which is where lecture files
     * are attached since lecture-level attachments were removed in favour of lecture units.
     */
    async openAttachmentUnitCreationPage(lectureId: number) {
        await this.openUnitsPage(lectureId);
        await this.openCreateUnit(UnitType.ATTACHMENT_VIDEO);
    }

    /**
     * Opens the creation form for a new unit of the specified type.
     * @param type - The type of unit to create.
     */
    async openCreateUnit(type: UnitType) {
        await this.getUnitCreationCard().locator(type).click();
    }

    /**
     * Retrieves a locator for the unit creation card.
     * @returns A Playwright locator for the unit creation card.
     */
    getUnitCreationCard() {
        return this.page.locator('[data-testid="unit-creation"]');
    }

    /**
     * Adds a new text unit with the specified name, text, and release date.
     * @param name - The name of the text unit.
     * @param text - The content of the text unit.
     * @param releaseDate - The release date of the text unit. Defaults to the current date.
     * @returns A promise that resolves with the response of the add action.
     */
    async addTextUnit(name: string, text: string, releaseDate = dayjs()) {
        await this.openCreateUnit(UnitType.TEXT);
        await this.page.fill('#name', name);
        await fillDateTimePicker(this.page.locator('#pick-releaseDate #date-input-field'), releaseDate);
        // Use the specific container for the content Monaco editor
        const contentField = this.page.locator('#content');
        await setMonacoEditorContentByLocator(this.page, contentField, text);
        return this.submitUnit();
    }

    /**
     * Adds a new exercise unit associated with the specified exercise identifier.
     * @param exerciseId - The identifier of the exercise to be associated with the new unit.
     * @returns A promise that resolves with the response of the add action.
     */
    async addExerciseUnit(exerciseId: number) {
        await this.openCreateUnit(UnitType.EXERCISE);
        const exerciseRow = `#exercise-${exerciseId}`;
        const exerciseUnit = this.page.locator(exerciseRow);
        await exerciseUnit.waitFor();
        await exerciseUnit.click();
        return this.submitUnit('#createButton');
    }

    /**
     * Creates a file/video content unit from a file through the creation form of the unit management page.
     * The unit is looked up in the lecture afterwards, because the body of a response to a file upload cannot
     * be read reliably (see readResponseJson).
     * @param lectureId - The lecture the unit management page belongs to.
     * @param name - The name of the unit.
     * @param filePath - Absolute path of the file to attach.
     * @returns A promise that resolves with the created unit.
     */
    async addAttachmentVideoUnit(lectureId: number, name: string, filePath: string) {
        await this.openCreateUnit(UnitType.ATTACHMENT_VIDEO);
        await this.getAttachmentFileInput().setInputFiles(filePath);
        await this.page.fill('#name', name);
        const responsePromise = this.page.waitForResponse(
            (response) => response.request().method() === 'POST' && /\/lecture\/lectures\/\d+\/attachment-video-units$/.test(new URL(response.url()).pathname),
        );
        await this.page.click('#submitButton');
        expect((await responsePromise).status()).toBe(201);
        const lectureResponse = await this.page.request.get(`${BASE_API}/lecture/lectures/${lectureId}/details`);
        const lecture = (await lectureResponse.json()) as Lecture;
        return lecture.lectureUnits!.find((unit) => unit.name === name) as AttachmentVideoUnit;
    }

    /**
     * Opens the edit form of a file/video content unit.
     */
    async openAttachmentVideoUnitEditPage(courseId: number, lectureId: number, unitId: number) {
        await Commands.gotoAndEnsureRendered(this.page, `/course-management/${courseId}/lectures/${lectureId}/unit-management/attachment-video-units/${unitId}/edit`);
        await this.page.getByTestId('current-file').waitFor({ state: 'visible', timeout: 30_000 });
    }

    /**
     * Replaces the file of the file/video content unit whose edit form is open and saves it.
     * @param filePath - Absolute path of the new file.
     * @returns A promise that resolves with the response of the update.
     */
    async replaceAttachmentVideoUnitFile(filePath: string) {
        await this.getAttachmentFileInput().setInputFiles(filePath);
        await this.page.getByTestId('replacement-file').waitFor({ state: 'visible' });
        const responsePromise = this.page.waitForResponse(
            (response) => response.request().method() === 'PUT' && /\/lecture\/lectures\/\d+\/attachment-video-units\/\d+$/.test(new URL(response.url()).pathname),
        );
        await this.page.click('#submitButton');
        return responsePromise;
    }

    /**
     * The hidden file input of the file/video content form, which its "Choose file" and "Replace file" buttons open.
     */
    getAttachmentFileInput() {
        return this.page.getByTestId('attachment-file-input');
    }

    /**
     * Submits the unit creation or editing form.
     * @param buttonId - The selector of the submit button. Defaults to '#submitButton'.
     * @returns A promise that resolves with the response of the submit action.
     */
    async submitUnit(buttonId = '#submitButton') {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/lecture/lectures/*/*`);
        await this.page.click(buttonId);
        await responsePromise;
    }

    private getLectureInfo(fieldName: string) {
        const selector = `//dt[span[contains(text(), "${fieldName}")]]/following-sibling::dd`;
        return this.page.locator(selector).first();
    }

    private getLectureDateInfo(fieldName: string) {
        const selector = `//dt[span[contains(text(), "${fieldName}")]]/following-sibling::dd/jhi-date-detail`;
        return this.page.locator(selector).first();
    }

    getLectureTitle() {
        const selector = `//dt[span[contains(text(), "Title")]]/following-sibling::dd/jhi-text-detail`;
        return this.page.locator(selector).first();
    }

    getLectureDescription() {
        return this.getLectureInfo('Description');
    }

    getLectureVisibleDate() {
        return this.getLectureDateInfo('Visible from');
    }

    getLectureStartDate() {
        return this.getLectureDateInfo('Start Date');
    }

    getLectureEndDate() {
        return this.getLectureDateInfo('End Date');
    }

    getLectureCourse() {
        return this.getLectureInfo('Course');
    }
}

/**
 * Enum for unit types, mapping to their respective button selectors.
 */
export enum UnitType {
    TEXT = '#createTextUnitButton',
    EXERCISE = '#createExerciseUnitButton',
    ONLINE = '#createOnlineUnitButton',
    ATTACHMENT_VIDEO = '#createAttachmentVideoUnitButton',
}
