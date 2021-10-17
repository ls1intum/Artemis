import { BASE_API, POST } from '../../../support/constants';
import { artemis } from '../../../support/ArtemisTesting';

// pageobjects
const modelingExerciseExampleSubmission = artemis.pageobjects.modelingExercise.assessmentEditor;
// requests
const courseManagementRequests = artemis.requests.courseManagement;
// Users
const userManagement = artemis.users;
const admin = userManagement.getAdmin();
const student = userManagement.getStudentOne();
const tutor = userManagement.getTutor();
const instructor = userManagement.getInstructor();

let course: any;
let modelingExercise: any;

describe('Modeling Exercise Spec', () => {
    before('Log in as admin and create a course', () => {
        cy.login(admin);
        courseManagementRequests.createCourse().then((courseResp) => {
            course = courseResp.body;
            courseManagementRequests.addStudentToCourse(course.id, student.username);
            courseManagementRequests.addInstructorToCourse(course.id, instructor);
        });
    });

    beforeEach('Create modeling exercise and submission', () => {
        courseManagementRequests.createModelingExercise({ course }).then((resp) => {
            modelingExercise = resp.body;
            cy.login(student);
            courseManagementRequests.startExerciseParticipation(course.id, modelingExercise.id).then((participationReponse: any) => {
                courseManagementRequests.makeModelingExerciseSubmission(modelingExercise.id, participationReponse.body);
            });
        });
    });

    after('Delete the test course', () => {
        cy.login(admin);
        courseManagementRequests.deleteCourse(course.id);
    });

    afterEach('Delete modeling exercise', () => {
        cy.login(instructor);
        courseManagementRequests.deleteModelingExercise(modelingExercise.id);
    });

    it.only('Tutor can assess the submission', () => {
        cy.login(tutor, '/course-management');
        cy.get(`[href="/course-management/${course.id}/assessment-dashboard"]`).click();
        cy.url().should('contain', `/course-management/${course.id}/assessment-dashboard`);
        cy.get('#field_showFinishedExercise').should('be.visible').click();
        cy.get('tbody > tr > :nth-child(6) >').click();
        cy.get('.btn').click();
        cy.wait(5000);
        cy.get('.btn').click();
        cy.get('#assessmentLockedCurrentUser').should('contain.text', 'You have the lock for this assessment');
        cy.get('jhi-unreferenced-feedback > .btn').click();
        cy.get('jhi-assessment-detail > .card > .card-body > :nth-child(1) > :nth-child(2)').clear().type('1');
        cy.get('jhi-assessment-detail > .card > .card-body > :nth-child(2) > :nth-child(2)').clear().type('thanks, i hate it');
        modelingExerciseExampleSubmission.openAssessmentForComponent(1);
        modelingExerciseExampleSubmission.assessComponent(-1, 'False');
        modelingExerciseExampleSubmission.openAssessmentForComponent(2);
        modelingExerciseExampleSubmission.assessComponent(2, 'Good');
        modelingExerciseExampleSubmission.openAssessmentForComponent(3);
        modelingExerciseExampleSubmission.assessComponent(0, 'Unnecessary');
        cy.get('[jhitranslate="entity.action.submit"]').click();
        // TODO: The alert is currently broken
        // cy.get('.alerts').should('contain.text', 'Your assessment was submitted successfully!');
    });

    it('Student can view the assessment and complain', () => {
        cy.login(student, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        cy.get('jhi-submission-result-status > .col-auto').should('contain.text', 'Score').and('contain.text', '2 of 10 points');
        cy.get('jhi-exercise-details-student-actions.col > > :nth-child(2)').click();
        cy.url().should('contain', `/courses/${course.id}/modeling-exercises/${modelingExercise.id}/participate/`);
        cy.get('.col-xl-8').should('contain.text', 'thanks, i hate it');
        cy.get('jhi-complaint-interactions > :nth-child(1) > .mt-4 > :nth-child(1)').click();
        cy.get('#complainTextArea').type('Thanks i hate you :^)');
        cy.intercept(POST, BASE_API + 'complaints').as('complaintCreated');
        cy.get('.col-6 > .btn').click();
        cy.wait('@complaintCreated');
    });

    it('Instructor can see complaint and reject it', () => {
        cy.login(instructor, `/course-management/${course.id}/assessment-dashboard`);
        cy.get(`[href="/course-management/${course.id}/complaints"]`).click();
        cy.get('tr > .text-center >').click();
        cy.get('#responseTextArea').type('lorem ipsum...');
        cy.get('#rejectComplaintButton').click();
        cy.get('.alerts').should('contain.text', 'Response to complaint has been submitted');
    });
});
