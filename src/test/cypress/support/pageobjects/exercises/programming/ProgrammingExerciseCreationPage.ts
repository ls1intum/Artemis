import { PROGRAMMING_EXERCISE_BASE } from '../../../requests/CourseManagementRequests';
import { BASE_API, POST, ProgrammingLanguage } from '../../../constants';

/**
 * A class which encapsulates UI selectors and actions for the programming exercise creation page.
 */
export class ProgrammingExerciseCreationPage {
    /**
     * @param title the title of the programming exercise
     */
    setTitle(title: string) {
        cy.get('#field_title').clear().type(title);
    }

    /**
     * @param shortName the short name of the programming exercise
     */
    setShortName(shortName: string) {
        cy.get('#field_shortName').clear().type(shortName);
    }

    /**
     * @param programmingLanguage the programming language of the programming exercise
     */
    setProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        cy.get('#field_programmingLanguage').select(programmingLanguage);
    }

    /**
     * @param packageName the package name of the programming exercise
     */
    setPackageName(packageName: string) {
        cy.get('#field_packageName').clear().type(packageName);
    }

    /**
     * @param points Achievable points in the exercise
     */
    setPoints(points: number) {
        cy.get('#field_points').clear().type(points.toString());
    }

    /**
     * Allows the usage of the online editor.
     */
    checkAllowOnlineEditor() {
        cy.get('#field_allowOnlineEditor').check();
    }

    /**
     * Generates the programming exercise.
     * @returns the chainable of the request to make further verifications
     */
    generate() {
        cy.intercept(POST, PROGRAMMING_EXERCISE_BASE + 'setup').as('createProgrammingExercise');
        cy.get('#save-entity').click();
        // Creating a programming exercise can take quite a while so we increase the default timeout here
        return cy.wait('@createProgrammingExercise', { timeout: 60000 });
    }

    import() {
        cy.intercept(POST, BASE_API + 'programming-exercises/import/*').as('programmingExerciseImport');
        cy.get('#save-entity').click();
        // Creating a programming exercise can take quite a while so we increase the default timeout here
        return cy.wait('@programmingExerciseImport', { timeout: 60000 });
    }
}
