import { Interception } from 'cypress/types/net-stubbing';
import dayjs from 'dayjs/esm';

import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';

import {
    courseManagement,
    courseManagementAPIRequest,
    courseManagementExercises,
    exerciseAPIRequest,
    navigationBar,
    textExerciseCreation,
    textExerciseExampleSubmissionCreation,
    textExerciseExampleSubmissions,
} from '../../../support/artemis';
import { admin } from '../../../support/users';
import { convertModelAfterMultiPart, generateUUID } from '../../../support/utils';

describe('Text exercise management', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
        });
    });

    it('Creates a text exercise in the UI', { scrollBehavior: 'center' }, () => {
        cy.visit('/');
        navigationBar.openCourseManagement();
        courseManagement.openExercisesOfCourse(course.id!);
        courseManagementExercises.createTextExercise();

        // Fill out text exercise form
        const exerciseTitle = 'text exercise' + generateUUID();
        textExerciseCreation.typeTitle(exerciseTitle);
        textExerciseCreation.setReleaseDate(dayjs());
        textExerciseCreation.setDueDate(dayjs().add(1, 'days'));
        textExerciseCreation.setAssessmentDueDate(dayjs().add(2, 'days'));
        textExerciseCreation.typeMaxPoints(10);
        const problemStatement = 'This is a problem statement';
        const exampleSolution = 'E = mc^2';
        textExerciseCreation.typeProblemStatement(problemStatement);
        textExerciseCreation.typeExampleSolution(exampleSolution);
        let exercise: TextExercise;
        textExerciseCreation.create().then((request: Interception) => {
            exercise = request.response!.body;
        });

        // Create an example submission
        courseManagementExercises.clickExampleSubmissionsButton();
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
            courseManagementExercises.getExercise(exercise.id!).should('be.visible');
        });
    });

    describe('Text exercise deletion', () => {
        let exercise: TextExercise;

        before('Create text exercise', () => {
            cy.login(admin, '/');
            exerciseAPIRequest.createTextExercise({ course }).then((response: Cypress.Response<TextExercise>) => {
                exercise = response.body;
            });
        });

        it('Deletes an existing text exercise', () => {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagement.openExercisesOfCourse(course.id!);
            courseManagementExercises.deleteTextExercise(exercise);
            courseManagementExercises.getExercise(exercise.id!).should('not.exist');
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
