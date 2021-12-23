import { Interception } from 'cypress/types/net-stubbing';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Course } from 'app/entities/course.model';
import { BASE_API } from '../../../support/constants';
import { DELETE } from '../../../support/constants';
import { generateUUID } from '../../../support/utils';
import { artemis } from '../../../support/ArtemisTesting';
import dayjs from 'dayjs';

// The user management object
const users = artemis.users;

// Requests
const courseManagement = artemis.requests.courseManagement;

// PageObjects
const textCreation = artemis.pageobjects.textExercise.creation;
const exampleSubmissions = artemis.pageobjects.textExercise.exampleSubmissions;
const exampleSubmissionCreation = artemis.pageobjects.textExercise.exampleSubmissionCreation;
const navigationBar = artemis.pageobjects.navigationBar;
const courseManagementPage = artemis.pageobjects.courseManagement;
const courseManagementExercises = artemis.pageobjects.courseManagementExercises;

describe('Text exercise management', () => {
    let course: Course;

    before(() => {
        cy.login(users.getAdmin());
        courseManagement.createCourse().then((response) => {
            course = response.body;
        });
    });

    it('Creates a text exercise in the UI', () => {
        cy.visit('/');
        navigationBar.openCourseManagement();
        courseManagementPage.openExercisesOfCourse(course.shortName!);
        cy.get('#create-text-exercise').click();

        // Fill out text exercise form
        const exerciseTitle = 'text exercise' + generateUUID();
        textCreation.typeTitle(exerciseTitle);
        textCreation.setReleaseDate(dayjs());
        textCreation.setDueDate(dayjs().add(1, 'days'));
        textCreation.setAssessmentDueDate(dayjs().add(2, 'days'));
        textCreation.typeMaxPoints(10);
        textCreation.checkAutomaticAssessmentSuggestions();
        const problemStatement = 'This is a problem statement';
        const exampleSolution = 'E = mc^2';
        textCreation.typeProblemStatement(problemStatement);
        textCreation.typeExampleSolution(exampleSolution);
        cy.get('#example-submission-message').should('be.visible');
        let exercise: TextExercise;
        textCreation.create().then((request: Interception) => {
            exercise = request.response!.body;
        });

        // Create an example submission
        exampleSubmissions.clickCreateExampleSubmission();
        exampleSubmissionCreation.showsExerciseTitle(exerciseTitle);
        exampleSubmissionCreation.showsProblemStatement(problemStatement);
        exampleSubmissionCreation.showsExampleSolution(exampleSolution);
        const submission = 'This is an\nexample\nsubmission';
        exampleSubmissionCreation.typeExampleSubmission(submission);
        exampleSubmissionCreation.clickCreateNewExampleSubmission().then((request: Interception) => {
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
            cy.login(users.getAdmin(), '/');
            courseManagement.createTextExercise({ course }).then((response: Cypress.Response<TextExercise>) => {
                exercise = response.body;
            });
        });

        it('Deletes an existing text exercise', () => {
            navigationBar.openCourseManagement();
            courseManagementPage.openExercisesOfCourse(course.shortName!);
            courseManagementExercises.clickDeleteExercise(exercise.id!);
            cy.get('#confirm-exercise-name').type(exercise.title!);
            cy.intercept(DELETE, BASE_API + 'text-exercises/*').as('deleteTextExercise');
            cy.get('#delete').click();
            cy.wait('@deleteTextExercise');
            cy.contains(exercise.title!).should('not.exist');
        });
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagement.deleteCourse(course.id!);
        }
    });
});
