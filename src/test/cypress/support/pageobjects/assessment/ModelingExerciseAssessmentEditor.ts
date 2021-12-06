import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';
import { MODELING_EDITOR_CANVAS } from '../exercises/modeling/ModelingEditor';
import { CypressExerciseType } from '../../requests/CourseManagementRequests';
import { BASE_API, PUT } from '../../constants';

const ASSESSMENT_CONTAINER = '#modeling-assessment-container';

/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Assessment editor
 */
export class ModelingExerciseAssessmentEditor extends AbstractExerciseAssessmentPage {
    openAssessmentForComponent(componentNumber: number) {
        cy.get('#apollon-assessment-row').getSettled(`${MODELING_EDITOR_CANVAS} >>> :nth-child(${componentNumber})`).children().eq(0).dblclick('top', { force: true });
    }

    assessComponent(points: number, feedback: string) {
        this.getPointAssessmentField().type(`${points}`);
        this.getFeedbackAssessmentField().type(`${feedback}`);
    }

    clickNextAssessment() {
        this.getNextAssessmentField().click();
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

    private getNextAssessmentField() {
        return this.getAssessmentContainer().children().last();
    }

    private getPointAssessmentField() {
        return this.getAssessmentContainer().children().eq(1).children().children().eq(1);
    }

    private getFeedbackAssessmentField() {
        return this.getAssessmentContainer().children().eq(3);
    }

    private getAssessmentContainer() {
        return cy.get(`${ASSESSMENT_CONTAINER}`);
    }
}
