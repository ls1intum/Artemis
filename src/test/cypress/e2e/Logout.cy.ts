import { artemis } from '../support/ArtemisTesting';
import { Course } from '../../../main/webapp/app/entities/course.model';
import { ModelingExercise } from '../../../main/webapp/app/entities/modeling-exercise.model';
import { convertCourseAfterMultiPart } from '../support/requests/CourseManagementRequests';

// Users
const users = artemis.users;
const admin = users.getAdmin();
const studentOne = users.getStudentOne();

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// PageObjects
const courseOverview = artemis.pageobjects.course.overview;
const modelingEditor = artemis.pageobjects.exercise.modeling.editor;

describe('Logout tests', () => {
    let course: Course;
    let modelingExercise: ModelingExercise;

    before('Login as admin and create a course with a modeling exercise', () => {
        cy.login(admin);

        courseManagementRequests.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequests.createModelingExercise({ course }).then((resp: Cypress.Response<ModelingExercise>) => {
                modelingExercise = resp.body;
            });
        });
    });

    after(() => {
        if (course) {
            cy.login(admin);
            courseManagementRequests.deleteCourse(course.id!);
        }
    });

    const startExerciseAndMakeChanges = () => {
        cy.login(studentOne);
        cy.visit(`/courses/${course.id}/exercises`);
        courseOverview.startExercise(modelingExercise.id!);
        courseOverview.openRunningExercise(modelingExercise.id!);
        modelingEditor.addComponentToModel(modelingExercise.id!, 1);
        modelingEditor.addComponentToModel(modelingExercise.id!, 2);
        cy.get('#account-menu').click().get('#logout').click();
    };

    it('Logs out by pressing OK when unsaved changes on exercise mode', () => {
        startExerciseAndMakeChanges();
        cy.on('window:confirm', (text) => {
            expect(text).to.contains('You have unsaved changes');
            return true;
        });
        cy.url().should('equal', Cypress.config().baseUrl + '/');
    });

    it('Stays logged in by pressing cancel when trying to logout during unsaved changes on exercise mode', () => {
        startExerciseAndMakeChanges();
        cy.on('window:confirm', (text) => {
            expect(text).to.contains('You have unsaved changes');
            return false;
        });
        cy.url().should('not.equal', Cypress.config().baseUrl + '/');
    });
});
