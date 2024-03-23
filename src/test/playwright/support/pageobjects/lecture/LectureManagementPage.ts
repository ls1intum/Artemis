import { Page } from 'playwright';
import dayjs from 'dayjs';
import { Lecture } from 'app/entities/lecture.model';
import { expect } from '@playwright/test';
import { BASE_API } from '../../constants';

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
        await this.getLecture(lecture.id!).locator('#delete-lecture').click();
        await expect(this.page.locator('#delete')).toBeDisabled();
        await this.page.fill('#confirm-entity-name', lecture.title!);
        const responsePromise = this.page.waitForResponse(`${BASE_API}/lectures/*`);
        await this.page.click('#delete');
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
     * @param lectureId - The identifier of the lecture to navigate to its units page.
     */
    async openUnitsPage(lectureId: number) {
        await this.getLecture(lectureId).locator('#units').click();
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
        return this.page.locator('#unit-creation');
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
        await this.page.fill('#pick-releaseDate #date-input-field', releaseDate.toString());
        const contentField = this.page.locator('.ace_content');
        await contentField.click();
        await contentField.pressSequentially(text);
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
     * Submits the unit creation or editing form.
     * @param buttonId - The selector of the submit button. Defaults to '#submitButton'.
     * @returns A promise that resolves with the response of the submit action.
     */
    async submitUnit(buttonId = '#submitButton') {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/lectures/*/*`);
        await this.page.click(buttonId);
        await responsePromise;
    }

    private getLectureInfo(fieldName: string) {
        const selector = `//dt[span[contains(text(), "${fieldName}")]]/following-sibling::dd`;
        return this.page.locator(selector).first();
    }

    getLectureTitle() {
        return this.getLectureInfo('Title');
    }

    getLectureDescription() {
        return this.getLectureInfo('Description');
    }

    getLectureVisibleDate() {
        return this.getLectureInfo('Visible from');
    }

    getLectureStartDate() {
        return this.getLectureInfo('Start Date');
    }

    getLectureEndDate() {
        return this.getLectureInfo('End Date');
    }

    getLectureCourse() {
        return this.getLectureInfo('Course');
    }
}

/**
 * Enum for unit types, mapping to their respective button selectors.
 */
enum UnitType {
    TEXT = '#createTextUnitButton',
    EXERCISE = '#createExerciseUnitButton',
    VIDEO = '#createVideoUnitButton',
    FILE = '#createFileUploadUnitButton',
}
