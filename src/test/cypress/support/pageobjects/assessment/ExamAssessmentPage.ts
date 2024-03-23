import { BASE_API, POST, PUT } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

export class ExamAssessmentPage extends AbstractExerciseAssessmentPage {
    submitModelingAssessment() {
        cy.intercept(PUT, `${BASE_API}/modeling-submissions/*/result/*/assessment*`).as('submitAssessment');
        super.submitWithoutInterception();
        return cy.wait('@submitAssessment');
    }

    submitTextAssessment() {
        cy.intercept(POST, `${BASE_API}/participations/*/results/*/submit-text-assessment`).as('submitFeedback');
        super.submitWithoutInterception();
        return cy.wait('@submitFeedback');
    }
}
