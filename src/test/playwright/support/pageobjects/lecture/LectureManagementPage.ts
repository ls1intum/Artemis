import { Page } from 'playwright';
import dayjs from 'dayjs/esm';
import { Lecture } from 'app/entities/lecture.model';
import { expect } from '@playwright/test';
import { BASE_API } from '../../constants';

export class LectureManagementPage {
    private page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async clickCreateLecture() {
        await this.page.click('#jh-create-entity');
    }

    async deleteLecture(lecture: Lecture) {
        await this.getLecture(lecture.id!).locator('#delete-lecture').click();
        await expect(this.page.locator('#delete')).toBeDisabled();
        await this.page.fill('#confirm-entity-name', lecture.title!);
        const responsePromise = this.page.waitForResponse(`${BASE_API}lectures/*`);
        await this.page.click('#delete');
        return await responsePromise;
    }

    getLectures() {
        return this.page.locator('#lectures');
    }

    getLecture(lectureId: number) {
        return this.page.locator(`#lecture-${lectureId}`);
    }

    getLectureContainer() {
        return this.page.locator('#lecture-preview');
    }

    async openUnitsPage(lectureId: number) {
        await this.getLecture(lectureId).locator('#units').click();
    }

    async openCreateUnit(type: UnitType) {
        await this.getUnitCreationCard().locator(type).click();
    }

    getUnitCreationCard() {
        return this.page.locator('#unit-creation');
    }

    async addTextUnit(name: string, text: string, releaseDate = dayjs()) {
        await this.openCreateUnit(UnitType.TEXT);
        await this.page.fill('#name', name);
        await this.page.fill('#pick-releaseDate #date-input-field', releaseDate.toString());
        await this.page.fill('.ace_content', text);
        return this.submitUnit();
    }

    async addExerciseUnit(exerciseId: number) {
        await this.openCreateUnit(UnitType.EXERCISE);
        const exerciseRow = `#exercise-${exerciseId}`;
        const exerciseUnit = this.page.locator(exerciseRow);
        await exerciseUnit.waitFor();
        await exerciseUnit.click();
        return this.submitUnit('#createButton');
    }

    async submitUnit(buttonId = '#submitButton') {
        const responsePromise = this.page.waitForResponse(BASE_API + 'lectures/*/*');
        await this.page.click(buttonId);
        await responsePromise;
    }
}

enum UnitType {
    TEXT = '#createTextUnitButton',
    EXERCISE = '#createExerciseUnitButton',
    VIDEO = '#createVideoUnitButton',
    FILE = '#createFileUploadUnitButton',
}
