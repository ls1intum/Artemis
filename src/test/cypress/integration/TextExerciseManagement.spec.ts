import { generateUUID } from '../support/utils';
import { artemis } from '../support/ArtemisTesting';
import dayjs from 'dayjs';

// The user management object
const users = artemis.users;

// Requests
const courseManagement = artemis.requests.courseManagement;

// PageObjects
const textCreation = artemis.pageobjects.textExerciseCreation;
const navigationBar = artemis.pageobjects.navigationBar;
const courseManagementPage = artemis.pageobjects.courseManagement;

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
        navigationBar.openCourseManagement();
        courseManagementPage.openExercisesOfCourse(courseName, courseShortName);
        cy.get('[jhitranslate="artemisApp.textExercise.home.createLabel"]').click();
        const exerciseTitle = 'text exercise';
        textCreation.typeTitle(exerciseTitle);
        textCreation.setReleaseDate(dayjs());
        textCreation.setDueDate(dayjs().add(1, 'days'));
        textCreation.setAssessmentDueDate(dayjs().add(2, 'days'));
        textCreation.typeMaxPoints(10);
        textCreation.checkAutomaticAssessmentSuggestions();
        textCreation.typeProblemStatement('This is a problem statement');
        textCreation.typeExampleSolution('E = mc^2');
        textCreation.typeAssessmentInstructions('Albert Einstein');
        textCreation.create().its('status').should('eq', 201);
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagement.deleteCourse(course.id);
        }
    });
});
