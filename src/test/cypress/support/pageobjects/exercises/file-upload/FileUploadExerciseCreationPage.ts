import { BASE_API } from '../../../constants';
import { Dayjs } from 'dayjs/esm';
import { POST } from '../../../constants';
import { enterDate } from '../../../utils';
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

    private typeText(selector: string, text: string) {
        cy.get(selector).find('.ace_content').type(text);
    }
}
