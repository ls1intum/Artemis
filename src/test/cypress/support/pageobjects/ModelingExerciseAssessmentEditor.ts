import { AbstractExerciseAssessmentPage } from './assessment/AbstractExerciseAssessmentPage';
import { MODELING_SPACE } from './ModelingEditor';
import { CypressExerciseType } from '../requests/CourseManagementRequests';
import { BASE_API, PUT } from '../constants';

// TODO: find or create better selectors for this
const FEEDBACK_CONTAINER = '.sc-lcuiOb';

/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Assessment editor
 */
export class ModelingExerciseAssessmentEditor extends AbstractExerciseAssessmentPage {
    openAssessmentForComponent(componentNumber: number) {
        cy.get('.apollon-row').getSettled(`${MODELING_SPACE} >> :nth-child(${componentNumber})`).children().eq(0).dblclick('top', { force: true });
    }

    assessComponent(points: number, feedback: string) {
        cy.get(`${FEEDBACK_CONTAINER} > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)`).type(`${points}`);
        cy.get(`${FEEDBACK_CONTAINER} > :nth-child(1) > :nth-child(3)`).type(`${feedback}`);
    }

    rejectComplaint(response: string) {
        return super.rejectComplaint(response, CypressExerciseType.MODELING);
    }
    acceptComplaint(response: string) {
        return super.acceptComplaint(response, CypressExerciseType.MODELING);
    }

    submit() {
        cy.intercept(PUT, BASE_API + 'modeling-submissions/*/example-assessment').as('createExampleSubmission');
        cy.contains('Save Example Assessment').click();
        return cy.wait('@createExampleSubmission').its('response.statusCode').should('eq', 200);
    }
}
