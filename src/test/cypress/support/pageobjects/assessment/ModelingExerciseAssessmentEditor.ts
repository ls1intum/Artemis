import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';
import { MODELING_SPACE } from '../exercises/modeling/ModelingEditor';
import { CypressExerciseType } from '../../requests/CourseManagementRequests';
import { BASE_API, PUT } from '../../constants';
// TODO: find or create better selectors for this
const FEEDBACK_CONTAINER = '.sc-lcepkR';

/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Assessment editor
 */
export class ModelingExerciseAssessmentEditor extends AbstractExerciseAssessmentPage {
    openAssessmentForComponent(componentNumber: number) {
        cy.get('.apollon-row').getSettled(`${MODELING_SPACE} >>> :nth-child(${componentNumber})`).children().eq(0).dblclick('top', { force: true });
    }

    assessComponent(points: number, feedback: string) {
        cy.get(`${FEEDBACK_CONTAINER} > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)`).type(`${points}`);
        cy.get(`${FEEDBACK_CONTAINER} > :nth-child(1) > :nth-child(4)`).type(`${feedback}`);
    }

    rejectComplaint(response: string) {
        return super.rejectComplaint(response, CypressExerciseType.MODELING);
    }
    acceptComplaint(response: string) {
        return super.acceptComplaint(response, CypressExerciseType.MODELING);
    }

    submitExample() {
        cy.intercept(PUT, BASE_API + 'modeling-submissions/*/example-assessment').as('createExampleSubmission');
        cy.contains('Save Example Assessment').click();
        return cy.wait('@createExampleSubmission').its('response.statusCode').should('eq', 200);
    }

    submit() {
        cy.intercept(PUT, BASE_API + 'modeling-submissions/*/result/*/assessment*').as('submitModelingAssessment');
        super.submitWithoutInterception();
        return cy.wait('@submitModelingAssessment').its('response.statusCode').should('eq', 200);
    }
}
