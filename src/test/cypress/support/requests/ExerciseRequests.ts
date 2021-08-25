import { BASE_API, DELETE, POST } from '../constants';
import programmingExerciseTemplate from '../../fixtures/requests/programming_exercise_template.json';
import textExerciseTemplate from '../../fixtures/requests/textExercise_template.json';

export const PROGRAMMING_EXERCISE_BASE = BASE_API + 'programming-exercises/';
const MODELING_EXERCISE_BASE = BASE_API + 'modeling-exercises/';

/**
 * Requests related to exercises.
 */
export abstract class ExerciseRequests {
    protected oneDay = 24 * 60 * 60 * 1000;

    /**
     * Creates programming exercise with the specified settings and adds it to the specified course or exam.
     * @param title the title of the programming exercise
     * @param programmingShortName the short name of the programming exercise
     * @param packageName the package name of the programming exercise
     * @param releaseDate when the programming exercise should be available (default is now)
     * @param dueDate when the programming exercise should be due (default is now + 1 day)
     * @param body an object containing either the course or exercise group the exercise will be added to
     * @returns <Chainable> request
     */
    protected createProgrammingExercise(
        title: string,
        programmingShortName: string,
        packageName: string,
        body: { course: any } | { exerciseGroup: any },
        releaseDate = new Date(),
        dueDate = new Date(Date.now() + this.oneDay),
    ) {
        const isExamExercise = body.hasOwnProperty('exerciseGroup');
        const programmingTemplate: any = Object.assign({}, programmingExerciseTemplate, body);
        programmingTemplate.title = title;
        programmingTemplate.shortName = programmingShortName;
        programmingTemplate.packageName = packageName;
        if (!isExamExercise) {
            programmingTemplate.releaseDate = releaseDate.toISOString();
            programmingTemplate.dueDate = dueDate.toISOString();
        } else {
            programmingTemplate.allowComplaintsForAutomaticAssessments = true;
        }

        const runsOnBamboo: boolean = Cypress.env('isBamboo');
        if (runsOnBamboo) {
            cy.waitForGroupSynchronization();
        }

        return cy.request({
            url: PROGRAMMING_EXERCISE_BASE + 'setup',
            method: POST,
            body: programmingTemplate,
        });
    }

    /**
     * Creates a text exercise with the specified settings and adds it to the specified course or exercise group (exam).
     * @param title title of the text exercise
     * @param body a json object containing the course or exercise group.
     * @returns <Chainable> request
     */
    protected createTextExercise(title: string, body: { course: any } | { exerciseGroup: any }) {
        const textExercise: any = Object.assign({}, textExerciseTemplate, body);
        textExercise.title = title;
        return cy.request({ method: POST, url: BASE_API + 'text-exercises', body: textExercise });
    }

    /**
     * Creates a modeling exercise with the specified settings and adds it to the specified course or exercise group (exam).
     * @param modelingExercise the modeling exercise object
     * @param body a json object containing the course or exercise group.
     * @returns <Chainable> request
     */
    protected createModelingExercise(modelingExercise: any, body: { course: any } | { exerciseGroup: any }) {
        const newModelingExercise = Object.assign({}, modelingExercise, body);
        return cy.request({
            url: MODELING_EXERCISE_BASE,
            method: POST,
            body: newModelingExercise,
        });
    }

    /**
     * Deletes the programming exercise with the specified id.
     * @param id the exercise id
     * @returns <Chainable> request
     */
    deleteProgrammingExercise(id: number) {
        return cy.request({ method: DELETE, url: PROGRAMMING_EXERCISE_BASE + id + '?deleteStudentReposBuildPlans=true&deleteBaseReposBuildPlans=true' });
    }

    /**
     * Deletes a modeling exercise.
     * @param exerciseID the exercise id
     * @returns <Chainable> request
     */
    deleteModelingExercise(exerciseID: number) {
        return cy.request({
            url: MODELING_EXERCISE_BASE + exerciseID,
            method: DELETE,
        });
    }
}
