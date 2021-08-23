import { DELETE } from '../../support/constants';
import { generateUUID } from '../../support/utils';
import { artemis } from '../../support/ArtemisTesting';
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

// Container for a course dto
let course: any;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;

describe('Text exercise management', () => {
    before(() => {
        cy.login(users.getAdmin());
        courseManagement.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
        });
    });

    it('Creates a text exercise in the UI', () => {
        cy.visit('/');
        navigationBar.openCourseManagement();
        courseManagementPage.openExercisesOfCourse(courseName, courseShortName);
        cy.get('[jhitranslate="artemisApp.textExercise.home.createLabel"]').click();

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
        const assessmentInstructions = 'Albert Einstein';
        textCreation.typeProblemStatement(problemStatement);
        textCreation.typeExampleSolution(exampleSolution);
        textCreation.typeAssessmentInstructions(assessmentInstructions);
        textCreation.create().its('response.statusCode').should('eq', 201);

        // Create an example submission
        exampleSubmissions.clickCreateExampleSubmission();
        cy.contains(exerciseTitle).should('be.visible');
        cy.contains(problemStatement).should('be.visible');
        cy.contains(exampleSolution).should('be.visible');
        cy.contains(assessmentInstructions).should('be.visible');
        const submission = 'This is an\nexample\nsubmission';
        exampleSubmissionCreation.typeExampleSubmission(submission);
        exampleSubmissionCreation
            .clickCreateNewExampleSubmission()
            .its('response')
            .should((response: any) => {
                expect(response.statusCode).to.equal(201);
                expect(response.body.submission.text).to.equal(submission);
            });

        // Make sure example submission is shown in the list
        cy.contains('Example Submissions Board').click();
        cy.contains('Example Submission 1').should('be.visible');

        // Make sure text exercise is shown in exercises list
        cy.visit(`course-management/${course.id}/exercises`);
        courseManagementExercises.getExerciseRowRootElement(exerciseTitle).should('be.visible');
    });

    describe('Text exercise deletion', () => {
        const exerciseTitle = 'text exercise' + generateUUID();

        beforeEach(() => {
            courseManagement.createTextExercise({ course }, exerciseTitle).its('status').should('eq', 201);
        });

        it('Deletes an existing programming exercise', function () {
            cy.login(users.getAdmin(), '/');
            navigationBar.openCourseManagement();
            courseManagementPage.openExercisesOfCourse(courseName, courseShortName);
            courseManagementExercises.clickDeleteExercise(exerciseTitle);
            cy.intercept(DELETE, '/api/text-exercises/*').as('deleteTextExercise');
            cy.get('[type="text"], [name="confirmExerciseName"]').type(exerciseTitle).type('{enter}');
            cy.wait('@deleteTextExercise').its('response.statusCode').should('eq', 200);
            cy.contains(exerciseTitle).should('not.exist');
        });
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagement.deleteCourse(course.id);
        }
    });
});
