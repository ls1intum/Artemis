import { BASE_API, DELETE, POST } from '../../constants';
import day from 'dayjs';

export class LectureManagementPage {
    clickCreateLecture() {
        cy.get('#jh-create-entity').click();
    }

    deleteLecture(lectureTitle: string) {
        this.getLectureRow(lectureTitle).find('.btn-danger').click();
        cy.get('.modal-footer').find('.btn-danger').should('be.disabled');
        cy.get('.modal-body').find('input').type(lectureTitle);
        cy.intercept(DELETE, `${BASE_API}lectures/*`).as('deleteLecture');
        cy.get('.modal-footer').find('.btn-danger').click();
        return cy.wait('@deleteLecture');
    }

    getLectureRow(lectureTitle: string) {
        return this.getLectureSelector(lectureTitle).parents('tr');
    }

    getLectureSelector(lectureTitle: string) {
        return this.getLectureContainer().contains(lectureTitle);
    }

    getLectureContainer() {
        return cy.get('.markdown-preview');
    }

    openUnitsPage(lectureTitle: string) {
        this.getLectureRow(lectureTitle).find('[jhitranslate="entity.action.units"]').click();
    }

    openCreateUnit(type: UnitType) {
        this.getUnitCreationCard().find(type).click();
    }

    getUnitCreationCard() {
        return cy.get('.creation-card');
    }

    addTextUnit(name: string, text: string, releaseDate = day()) {
        this.openCreateUnit(UnitType.TEXT);
        cy.get('#name').type(name);
        cy.get('[name="datePicker"]').type(releaseDate.toString());
        cy.get('.ace_content').type(text, { parseSpecialCharSequences: false });
        return this.submitUnit();
    }

    addExerciseUnit(exerciseId: number) {
        this.openCreateUnit(UnitType.EXERCISE);
        cy.contains(exerciseId).click();
        return this.submitUnit();
    }

    submitUnit() {
        cy.intercept(POST, BASE_API + 'lectures/*/*').as('createUnit');
        cy.get('.btn-primary').click();
        return cy.wait('@createUnit');
    }
}

enum UnitType {
    TEXT = '#createTextUnitButton',
    EXERCISE = '#createExerciseUnitButton',
    VIDEO = '#createVideoUnitButton',
    FILE = '#createFileUploadUnitButton',
}
