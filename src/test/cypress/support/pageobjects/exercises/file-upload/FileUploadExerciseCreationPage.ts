import { BASE_API } from '../../../constants';
import { Dayjs } from 'dayjs/esm';
import { POST } from '../../../constants';
import { dayjsToString } from '../../../utils';
/**
 * A class which encapsulates UI selectors and actions for the file upload exercise creation page.
 */
export class FileUploadExerciseCreationPage {
    /**
     * @param title the title of the text exercise
     */
    typeTitle(title: string) {
        cy.get('#field_title').clear().type(title);
    }

    setReleaseDate(date: Dayjs) {
        this.typeDate(date, '#release-date');
    }

    setDueDate(date: Dayjs) {
        this.typeDate(date, '#due-date');
    }

    setAssessmentDueDate(date: Dayjs) {
        this.typeDate(date, '#assessment-due-date');
    }

    typeMaxPoints(maxPoints: number) {
        cy.get('#field_points').type(maxPoints.toString());
    }

    setFilePattern(pattern: string) {
        this.typeText('#field_filePattern', pattern);
    }

    typeProblemStatement(statement: string) {
        this.typeText('#field_problemStatement', statement);
    }

    typeExampleSolution(statement: string) {
        this.typeText('#field_exampleSolution', statement);
    }

    typeAssessmentInstructions(statement: string) {
        this.typeText('#gradingInstructions', statement);
    }

    create() {
        cy.intercept(POST, BASE_API + 'file-upload-exercises').as('fileUploadExerciseCreation');
        cy.get('#save-entity').click();
        return cy.wait('@fileUploadExerciseCreation');
    }

    private typeDate(date: Dayjs, inputSelector: string) {
        cy.get(inputSelector).find('#date-input-field').clear().type(dayjsToString(date), { force: true });
    }

    private typeText(selector: string, text: string) {
        cy.get(selector).find('.ace_content').type(text);
    }
}
