import { BASE_API } from '../../../support/constants';
import { artemis } from '../../../support/ArtemisTesting';

// pageobjects
const courseManagement = artemis.pageobjects.courseManagement;
const modelingEditor = artemis.pageobjects.modelingExercise.editor;
// requests
const courseManagementRequests = artemis.requests.courseManagement;
// Users
const userManagement = artemis.users;
const admin = userManagement.getAdmin();
const student = userManagement.getStudentOne();
let course: any;
let modelingExercise: any;

describe('Modeling Exercise Spec', () => {
    before('Log in as admin and create a course', () => {
        cy.login(admin);
        courseManagementRequests.createCourse().then((courseResp: any) => {
            course = courseResp.body;
            cy.visit(`/course-management/${course.id}`).get('.row-md > :nth-child(2)').should('contain.text', course.title);
            courseManagement.addStudentToCourse(student);
            courseManagementRequests.createModelingExercise({ course }).then((resp: any) => {
                modelingExercise = resp.body;
            });
        });
    });

    after('Delete the test course', () => {
        cy.login(admin);
        courseManagementRequests.deleteCourse(course.id);
    });

    it('Student can start and submit their model', () => {
        cy.login(student, `/courses/${course.id}`);
        cy.get('.col-lg-8').contains(modelingExercise.title);
        cy.intercept(BASE_API + 'courses/*/exercises/*/participations').as('createModelingParticipation');
        cy.get('.btn-sm').should('contain.text', 'Start exercise').click();
        cy.wait('@createModelingParticipation');
        cy.get('.course-exercise-row').find('.btn-primary').should('contain.text', 'Open modelling editor').click();
        modelingEditor.addComponentToModel(1);
        modelingEditor.addComponentToModel(2);
        modelingEditor.addComponentToModel(3);
        modelingEditor.submit();
        cy.get('.alerts').should('contain.text', 'Your submission was successful! You can change your submission or wait for your feedback.');
        cy.contains('No graded result').should('be.visible');
    });
});
