import { BASE_API, DELETE } from '../../constants';

export class LectureManagementPage {
    clickCreateLecture() {
        cy.get('.create-lecture').click();
    }

    deleteLecture(lectureTitle: string) {
        this.getLectureRow(lectureTitle).find('.btn-danger').click();
        cy.get('.modal-footer').find('.btn-danger').should('be.disabled');
        cy.get('.modal-body').find('input').type(lectureTitle);
        cy.intercept(DELETE, `${BASE_API}lectures/*`).as('deleteLecture');
        cy.get('.modal-footer').find('.btn-danger').should('not.be.disabled').click();
        return cy.wait('@deleteLecture');
    }

    getLectureRow(lectureTitle: string) {
        return this.getLectureSelector(lectureTitle).parents('tr');
    }

    getLectureSelector(lectureTitle: string) {
        return cy.get('.markdown-preview').contains(lectureTitle);
    }
}
