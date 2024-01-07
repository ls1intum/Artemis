/**
 * A class which encapsulates UI selectors and actions for the student assessment page.
 */
export class StudentAssessmentPage {
    startComplaint() {
        cy.get('#complain', { timeout: 30000 }).click();
    }

    enterComplaint(text: string) {
        cy.get('#complainTextArea').type(text);
    }

    submitComplaint() {
        cy.get('#submit-complaint').click();
    }

    getComplaintBadge() {
        return cy.get('jhi-complaint-request .badge');
    }

    getComplaintResponse() {
        return cy.get('#complainResponseTextArea');
    }
}
