import { BASE_API } from '../../../constants';
import { Dayjs } from 'dayjs/esm';
import { POST } from '../../../constants';
import { dayjsToString } from '../../../utils';
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
        this.typeDate(date, '#pick-releaseDate');
    }

    setDueDate(date: Dayjs) {
        this.typeDate(date, '#pick-dueDate');
    }

    setAssessmentDueDate(date: Dayjs) {
        this.typeDate(date, '#pick-assessmentDueDate');
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
        this.typeText('#exampleSolution', statement);
    }

    typeAssessmentInstructions(statement: string) {
        this.typeText('#gradingInstructions', statement);
    }

    create() {
        cy.intercept(POST, BASE_API + 'text-exercises').as('textExerciseCreation');
        cy.get('#submit-entity').click();
        return cy.wait('@textExerciseCreation');
    }

    private typeDate(date: Dayjs, inputSelector: string) {
        cy.get(inputSelector).find('#date-input-field').clear().type(dayjsToString(date), { force: true });
    }

    private typeText(selector: string, text: string) {
        cy.get(selector).find('.ace_content').type(text);
    }
}
