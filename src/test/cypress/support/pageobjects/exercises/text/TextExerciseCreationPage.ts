import { Dayjs } from 'dayjs/esm';

import { BASE_API } from '../../../constants';
import { POST } from '../../../constants';
import { enterDate } from '../../../utils';

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
        enterDate('#pick-releaseDate', date);
    }

    setDueDate(date: Dayjs) {
        enterDate('#pick-dueDate', date);
    }

    setAssessmentDueDate(date: Dayjs) {
        enterDate('#pick-assessmentDueDate', date);
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

    import() {
        cy.intercept(POST, BASE_API + 'text-exercises/import/*').as('textExerciseImport');
        cy.get('#submit-entity').click();
        return cy.wait('@textExerciseImport');
    }

    private typeText(selector: string, text: string) {
        cy.get(selector).find('.ace_content').type(text);
    }
}
