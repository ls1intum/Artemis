import { Interception } from 'cypress/types/net-stubbing';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Course } from 'app/entities/course.model';
import { BASE_API } from '../../../support/constants';
import {
    courseManagement,
    courseManagementExercises,
    courseManagementRequest,
    navigationBar,
    textExerciseCreation,
    textExerciseExampleSubmissionCreation,
    textExerciseExampleSubmissions,
} from '../../../support/artemis';
import { DELETE } from '../../../support/constants';
import { generateUUID } from '../../../support/utils';
import dayjs from 'dayjs/esm';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { admin } from '../../../support/users';

describe('Text exercise management', () => {
    let course: Course;

    before(() => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertCourseAfterMultiPart(response);
        });
    });

    it('Creates a text exercise in the UI', () => {
        cy.visit('/');
        navigationBar.openCourseManagement();
        courseManagement.openExercisesOfCourse(course.shortName!);
        courseManagementExercises.createTextExercise();

        // Fill out text exercise form
        const exerciseTitle = 'text exercise' + generateUUID();
        textExerciseCreation.typeTitle(exerciseTitle);
        textExerciseCreation.setReleaseDate(dayjs());
        textExerciseCreation.setDueDate(dayjs().add(1, 'days'));
        textExerciseCreation.setAssessmentDueDate(dayjs().add(2, 'days'));
        textExerciseCreation.typeMaxPoints(10);
        textExerciseCreation.checkAutomaticAssessmentSuggestions();
        const problemStatement = 'This is a problem statement';
        const exampleSolution = 'E = mc^2';
        textExerciseCreation.typeProblemStatement(problemStatement);
        textExerciseCreation.typeExampleSolution(exampleSolution);
        let exercise: TextExercise;
        textExerciseCreation.create().then((request: Interception) => {
            exercise = request.response!.body;
        });

        // Create an example submission
        cy.get('#example-submissions-button').click();
        textExerciseExampleSubmissions.clickCreateExampleSubmission();
        textExerciseExampleSubmissionCreation.showsExerciseTitle(exerciseTitle);
        textExerciseExampleSubmissionCreation.showsProblemStatement(problemStatement);
        textExerciseExampleSubmissionCreation.showsExampleSolution(exampleSolution);
        const submission = 'This is an\nexample\nsubmission';
        textExerciseExampleSubmissionCreation.typeExampleSubmission(submission);
        textExerciseExampleSubmissionCreation.clickCreateNewExampleSubmission().then((request: Interception) => {
            expect(request.response!.statusCode).to.eq(200);
            expect(request.response!.body.submission.text).to.equal(submission);
        });

        // Make sure text exercise is shown in exercises list
        cy.visit(`course-management/${course.id}/exercises`).then(() => {
            courseManagementExercises.getExerciseRowRootElement(exercise.id!).should('be.visible');
        });
    });

    describe('Text exercise deletion', () => {
        let exercise: TextExercise;

        beforeEach(() => {
            cy.login(admin, '/');
            courseManagementRequest.createTextExercise({ course }).then((response: Cypress.Response<TextExercise>) => {
                exercise = response.body;
            });
        });

        it('Deletes an existing text exercise', () => {
            navigationBar.openCourseManagement();
            courseManagement.openExercisesOfCourse(course.shortName!);
            courseManagementExercises.clickDeleteExercise(exercise.id!);
            cy.get('#confirm-exercise-name').type(exercise.title!);
            cy.intercept(DELETE, BASE_API + 'text-exercises/*').as('deleteTextExercise');
            cy.get('#delete').click();
            cy.wait('@deleteTextExercise');
            courseManagementExercises.getExerciseRowRootElement(exercise.id!).should('not.exist');
        });
    });

    after(() => {
        if (course) {
            cy.login(admin);
            courseManagementRequest.deleteCourse(course.id!);
        }
    });
});
