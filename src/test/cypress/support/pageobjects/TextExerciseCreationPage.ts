import { Dayjs } from 'dayjs';
import { POST } from '../constants';
import { dayjsToString } from '../utils';
/**
 * A class which encapsulates UI selectors and actions for the text exercise creation page.
 */
export class TextExerciseCreationPage {
    /**
     * @param title the title of the text exercise
     */
    typeTitle(title: string) {
        cy.get('#field_title').clear().type(title);
    }

    setReleaseDate(date: Dayjs) {
        this.typeDate(date, 'releaseDate');
    }

    setDueDate(date: Dayjs) {
        this.typeDate(date, 'dueDate');
    }

    setAssessmentDueDate(date: Dayjs) {
        this.typeDate(date, 'assessmentDueDate');
    }

    typeMaxPoints(maxPoints: number) {
        cy.get('#field_points').type(maxPoints.toString());
    }

    checkAutomaticAssessmentSuggestions() {
        cy.get('#automatic_assessment_enabled').check();
    }

    typeProblemStatement(statement: string) {
        this.typeText('#problemStatement', statement);
    }

    typeExampleSolution(statement: string) {
        this.typeText('#sampleSolution', statement);
    }

    typeAssessmentInstructions(statement: string) {
        this.typeText('#field_gradingInstructions', statement);
    }

    create() {
        cy.get('[jhitranslate="artemisApp.textExercise.exampleSubmissionsRequireExercise"]').should('be.visible');
        cy.intercept(POST, 'api/text-exercises').as('textExerciseCreation');
        cy.get('#submit-entity').click();
        return cy.wait('@textExerciseCreation');
    }

    clickCreateExampleSubmission() {
        cy.get('[jhitranslate="artemisApp.modelingExercise.createExampleSubmission"]').click();
    }

    private typeDate(date: Dayjs, identifier: string) {
        cy.get(`jhi-date-time-picker, [name="${identifier}"]`).find('input').clear().type(dayjsToString(date), { force: true });
    }

    private typeText(selector: string, text: string) {
        cy.get(selector).find('.ace_content').type(text);
    }
}
