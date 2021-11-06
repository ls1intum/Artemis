import { BASE_API, POST } from '../../constants';
import { Dayjs } from 'dayjs';

export class LectureCreationPage {
    setTitle(title: string) {
        cy.get('#field_title').type(title);
    }

    save() {
        cy.intercept(POST, BASE_API + 'lectures').as('createLecture');
        cy.get('#save-entity').click();
        return cy.wait('@createLecture');
    }

    typeDescription(description: string) {
        cy.get('.ace_content').type(description, { parseSpecialCharSequences: false });
    }

    setStartDate(date: Dayjs) {
        cy.get('[name="startDate"]').children().eq(2).type(date.toString());
    }

    setEndDate(date: Dayjs) {
        cy.get('[name="endDate"]').children().eq(2).type(date.toString());
    }
}
