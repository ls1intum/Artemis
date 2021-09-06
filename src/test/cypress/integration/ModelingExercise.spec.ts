import { POST, BASE_API } from './../support/constants';
import { dayjsToString } from '../support/utils';
import { artemis } from '../support/ArtemisTesting';

// https://day.js.org/docs is a tool for date/time
import dayjs from 'dayjs';

// pageobjects
const courseManagement = artemis.pageobjects.courseManagement;
const createModelingExercise = artemis.pageobjects.createModelingExercise;
const modelingExerciseExampleSubmission = artemis.pageobjects.modelingExerciseAssessmentEditor;
const modelingEditor = artemis.pageobjects.modelingEditor;
// requests
const courseManagementRequests = artemis.requests.courseManagement;
// Users
const userManagement = artemis.users;
const admin = userManagement.getAdmin();
const student = userManagement.getStudentOne();
const tutor = userManagement.getTutor();
const instructor = userManagement.getInstructor();

let testCourse: any;
let modelingExercise: any;

const modelingExerciseTitle = 'Cypress Modeling Exercise';

describe('Modeling Exercise Spec', () => {
    before('Log in as admin and create a course', () => {
        cy.login(admin);
        courseManagementRequests.createCourse().then((courseResp) => {
            testCourse = courseResp.body;
            cy.visit(`/course-management/${testCourse.id}`).get('.row-md > :nth-child(2)').should('contain.text', testCourse.title);
            courseManagement.addTutorToCourse(tutor);
            courseManagement.addStudentToCourse(student);
            courseManagement.addInstructorToCourse(instructor);
        });
    });

    after('Delete the test course', () => {
        cy.login(admin);
        courseManagementRequests.deleteCourse(testCourse.id);
    });

    describe('Create/Edit Modeling Exercise', () => {
        beforeEach('Login as instructor', () => {
            cy.login(instructor);
        });

        after('Delete Modeling Exercise', () => {
            cy.login(admin);
            courseManagementRequests.deleteModelingExercise(modelingExercise.id);
        });

        it('Create a new modeling exercise', () => {
            cy.visit(`/course-management/${testCourse.id}/exercises`);
            cy.get('#modeling-exercise-create-button').click();
            createModelingExercise.setTitle(modelingExerciseTitle);
            createModelingExercise.addCategories(['e2e-testing', 'test2']);
            createModelingExercise.setPoints(10);
            createModelingExercise
                .save()
                .its('response.body')
                .then((body) => {
                    modelingExercise = body;
                });
            cy.contains(modelingExerciseTitle).should('exist');
        });

        it('Create Example Solution', () => {
            cy.visit(`/course-management/${testCourse.id}/exercises`);
            cy.contains(modelingExerciseTitle).click();
            cy.get('.card-body').contains('Edit').click();
            modelingEditor.addComponentToModel(1);
            createModelingExercise.save();
            cy.get('[jhitranslate="entity.action.export"]').should('be.visible');
            cy.get('.sc-furvIG > :nth-child(1)').should('exist');
        });

        it('Creates Example Submission', () => {
            cy.visit(`/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/example-submissions`);
            cy.get('[jhitranslate="artemisApp.modelingExercise.createExampleSubmission"]').click();
            modelingEditor.addComponentToModel(1);
            modelingEditor.addComponentToModel(2);
            modelingEditor.addComponentToModel(3);
            cy.get('[jhitranslate="artemisApp.modelingExercise.createNewExampleSubmission"]').click();
            cy.get('.alerts').should('contain', 'Your diagram was saved successfully');
            cy.get('[jhitranslate="artemisApp.modelingExercise.showExampleAssessment"]').click();
            modelingExerciseExampleSubmission.openAssessmentForComponent(1);
            modelingExerciseExampleSubmission.assessComponent(-1, 'False');
            modelingExerciseExampleSubmission.openAssessmentForComponent(2);
            modelingExerciseExampleSubmission.assessComponent(2, 'Good');
            modelingExerciseExampleSubmission.openAssessmentForComponent(3);
            modelingExerciseExampleSubmission.assessComponent(0, 'Unnecessary');
            cy.contains('Save Example Assessment').click();
        });

        it('Edit Existing Modeling Exercise', () => {
            cy.visit(`/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/edit`);
            const newTitle = 'Cypress EDITED ME';
            const points = 100;
            createModelingExercise.setTitle(newTitle);
            createModelingExercise.pickDifficulty({ hard: true });
            createModelingExercise.setReleaseDate(dayjsToString(dayjs().add(1, 'day')));
            createModelingExercise.setDueDate(dayjsToString(dayjs().add(2, 'day')));
            createModelingExercise.setAssessmentDueDate(dayjsToString(dayjs().add(3, 'day')));
            createModelingExercise.includeInOverallScore();
            createModelingExercise.setPoints(points);
            createModelingExercise.save();
            cy.visit(`/course-management/${testCourse.id}/exercises`);
            cy.get('tbody > tr > :nth-child(2)').should('contain.text', newTitle);
            cy.get('tbody > tr > :nth-child(6)').should('contain.text', points.toString());
        });
    });

    describe('Modeling Exercise Flow', () => {
        before('Create Modeling Exercise with future release date', () => {
            courseManagementRequests.createModelingExercise({ course: testCourse }, modelingExerciseTitle, dayjs().add(1, 'hour')).then((resp) => {
                modelingExercise = resp.body;
            });
        });

        it('Student can not see unreleased Modeling Exercise', () => {
            cy.login(student, '/courses');
            cy.get('.card-body').contains(testCourse.title).click({ force: true });
            cy.contains('No exercises available for the course.').should('be.visible');
        });

        it('Release a Modeling Exercise', () => {
            cy.login(instructor, `/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/edit`);
            createModelingExercise.setReleaseDate(dayjsToString(dayjs().subtract(1, 'hour')));
            createModelingExercise.save();
        });

        it('Student can start and submit their model', () => {
            cy.intercept(BASE_API + 'courses/*/exercises/*/participations').as('createModelingParticipation');
            cy.login(student, `/courses/${testCourse.id}`);
            cy.get('.col-lg-8').contains(modelingExercise.title).click();
            cy.contains('Start exercise').click();
            cy.wait('@createModelingParticipation');
            cy.get('.btn').should('contain.text', 'Open modelling editor').click();
            modelingEditor.addComponentToModel(1);
            modelingEditor.addComponentToModel(2);
            modelingEditor.addComponentToModel(3);
            cy.get('.submission-button').click();
            cy.get('.alerts').should('contain.text', 'Your submission was successful! You can change your submission or wait for your feedback.');
            cy.contains('No graded result').should('be.visible');
        });

        it('Close exercise for submissions', () => {
            cy.login(instructor, `/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/edit`);
            createModelingExercise.setDueDate(dayjsToString(dayjs().add(1, 'second')));
            // so the submission is not considered 'late'
            cy.wait(1000);
            createModelingExercise.save();
            cy.url().should('contain', '/modeling-exercises/');
        });

        it('Tutor can assess the submission', () => {
            cy.login(tutor, '/course-management');
            cy.get(`[href="/course-management/${testCourse.id}/assessment-dashboard"]`).click();
            cy.url().should('contain', `/course-management/${testCourse.id}/assessment-dashboard`);
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
            cy.get('.top-container > :nth-child(3) > :nth-child(4)').click();
            cy.get('.alerts').should('contain.text', 'Your assessment was submitted successfully!');
        });

        it('Close assessment period', () => {
            cy.login(instructor, `/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/edit`);
            createModelingExercise.setAssessmentDueDate(dayjsToString(dayjs()));
            createModelingExercise.save();
        });

        it('Student can view the assessment and complain', () => {
            cy.login(student, `/courses/${testCourse.id}/exercises/${modelingExercise.id}`);
            cy.get('jhi-submission-result-status > .col-auto').should('contain.text', 'Score').and('contain.text', '2 of 10 points');
            cy.get('jhi-exercise-details-student-actions.col > > :nth-child(2)').click();
            cy.url().should('contain', `/courses/${testCourse.id}/modeling-exercises/${modelingExercise.id}/participate/`);
            cy.get('.col-xl-8').should('contain.text', 'thanks, i hate it');
            cy.get('jhi-complaint-interactions > :nth-child(1) > .mt-4 > :nth-child(1)').click();
            cy.get('#complainTextArea').type('Thanks i hate you :^)');
            cy.intercept(POST, BASE_API + 'complaints').as('complaintCreated');
            cy.get('.col-6 > .btn').click();
            cy.wait('@complaintCreated');
        });

        it('Instructor can see complaint and reject it', () => {
            cy.login(instructor, `/course-management/${testCourse.id}/assessment-dashboard`);
            cy.get(`[href="/course-management/${testCourse.id}/complaints"]`).click();
            cy.get('tr > .text-center >').click();
            cy.get('#responseTextArea').type('lorem ipsum...');
            cy.get('#rejectComplaintButton').click();
            cy.get('.alerts').should('contain.text', 'Response to complaint has been submitted');
        });
    });
});
