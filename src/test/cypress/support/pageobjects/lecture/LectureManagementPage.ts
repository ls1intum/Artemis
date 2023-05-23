import { Lecture } from 'app/entities/lecture.model';
import { BASE_API, DELETE, POST } from '../../constants';
import day from 'dayjs/esm';

export class LectureManagementPage {
    clickCreateLecture() {
        cy.get('#jh-create-entity').click();
    }

    deleteLecture(lecture: Lecture) {
        cy.get(`#lecture-${lecture.id}`).find('#delete-lecture').click();
        cy.get('#delete').should('be.disabled');
        cy.get('#confirm-exercise-name').type(lecture.title!);
        cy.intercept(DELETE, `${BASE_API}lectures/*`).as('deleteLecture');
        cy.get('#delete').click();
        return cy.wait('@deleteLecture');
    }

    getLecture(lecture: Lecture) {
        return cy.get(`#lecture-${lecture.id}`);
    }

    openUnitsPage(lectureID: number) {
        cy.get(`#lecture-${lectureID}`).find('#units').click();
    }

    openCreateUnit(type: UnitType) {
        this.getUnitCreationCard().find(type).click();
    }

    getUnitCreationCard() {
        return cy.get('#unit-creation');
    }

    addTextUnit(name: string, text: string, releaseDate = day()) {
        this.openCreateUnit(UnitType.TEXT);
        cy.get('#name').type(name);
        cy.get('#pick-releaseDate').find('#date-input-field').type(releaseDate.toString());
        cy.get('.ace_content').type(text, { parseSpecialCharSequences: false });
        return this.submitUnit();
    }

    addExerciseUnit(exerciseId: number) {
        this.openCreateUnit(UnitType.EXERCISE);
        const exerciseRow = '#exercise-' + exerciseId;
        cy.reloadUntilFound(exerciseRow);
        cy.get(exerciseRow).click();
        return this.submitUnit('#createButton');
    }

    submitUnit(buttonId = '#submitButton') {
        cy.intercept(POST, BASE_API + 'lectures/*/*').as('createUnit');
        cy.get(buttonId).click();
        return cy.wait('@createUnit');
    }
}

enum UnitType {
    TEXT = '#createTextUnitButton',
    EXERCISE = '#createExerciseUnitButton',
    VIDEO = '#createVideoUnitButton',
    FILE = '#createFileUploadUnitButton',
}
