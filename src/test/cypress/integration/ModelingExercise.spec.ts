import { generateUUID } from '../support/utils';
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

const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cy' + uid;

describe('Modeling Exercise Spec', () => {
    before('Log in as admin and create a course', () => {
        cy.intercept('POST', '/api/modeling-exercises').as('createModelingExercise');
        cy.login(admin);
        courseManagementRequests.createCourse(courseName, courseShortName).then((courseResp) => {
            testCourse = courseResp.body;
            cy.visit(`/course-management/${testCourse.id}`).get('.row-md > :nth-child(2)').should('contain.text', testCourse.title);
            // set tutor group
            courseManagement.addTutorToCourse(tutor);
            // set student group
            courseManagement.addStudentToCourse(student);
            // set instructor group
            courseManagement.addInstructorToCourse(instructor);
        });
    });

    after('Delete the test course', () => {
        cy.login(admin);
        courseManagementRequests.deleteCourse(testCourse.id);
    });

    describe('Create/Edit Modeling Exercise', () => {
        beforeEach('login as instructor', () => {
            cy.login(instructor);
        });

        after('delete Modeling Exercise', () => {
            cy.login(admin);
            courseManagementRequests.deleteModelingExercise(modelingExercise.id);
        });

        it('Create a new modeling exercise', () => {
            cy.intercept('POST', '/api/modeling-exercises').as('createModelingExercise');
            cy.visit(`/course-management/${testCourse.id}/exercises`);
            cy.get('#modeling-exercise-create-button').click();
            createModelingExercise.setTitle('Cypress Modeling Exercise');
            createModelingExercise.addCategories(['e2e-testing', 'test2']);
            createModelingExercise.setPoints(10);
            createModelingExercise.save();
            cy.wait('@createModelingExercise').then((interception) => {
                modelingExercise = interception?.response?.body;
            });
            cy.contains('Cypress Modeling Exercise').should('exist');
        });

        it('Create Example Solution', () => {
            cy.visit(`/course-management/${testCourse.id}/exercises`);
            cy.contains('Cypress Modeling Exercise').click();
            cy.get('.card-body').contains('Edit').click();
            modelingEditor.addComponentToModel(1);
            createModelingExercise.save();
            cy.get('.row-md > :nth-child(4)').should('contain.text', 'Export');
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
            cy.intercept('PUT', '/api/modeling-exercises').as('editModelingExercise');
            cy.visit(`/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/edit`);
            createModelingExercise.setTitle('Cypress EDITED ME');
            createModelingExercise.pickDifficulty({ hard: true });
            createModelingExercise.setReleaseDate(dayjs().add(1, 'day').format('YYYY-MM-DDTHH:mm:ss.SSS'));
            createModelingExercise.setDueDate(dayjs().add(2, 'day').format('YYYY-MM-DDTHH:mm:ss.SSS'));
            createModelingExercise.setAssessmentDueDate(dayjs().add(3, 'day').format('YYYY-MM-DDTHH:mm:ss.SSS'));
            createModelingExercise.includeInOverallScore();
            createModelingExercise.setPoints(100);
            createModelingExercise.save();
            cy.wait('@editModelingExercise');
            cy.visit(`/course-management/${testCourse.id}/exercises`);
            cy.get('tbody > tr > :nth-child(2)').should('contain.text', 'Cypress EDITED ME');
            cy.get('tbody > tr > :nth-child(6)').should('contain.text', '100');
        });
    });

    describe('Modeling Exercise Flow', () => {
        before('create Modeling Exercise with future release date', () => {
            cy.fixture('requests/modelingExercise_template.json').then((exercise) => {
                exercise.title = 'Cypress Modeling Exercise ' + uid;
                exercise.releaseDate = dayjs().add(1, 'day').format('YYYY-MM-DDTHH:mm:ss.SSS[Z]');
                exercise.dueDate = dayjs().add(2, 'day').format('YYYY-MM-DDTHH:mm:ss.SSS[Z]');
                exercise.assessmentDueDate = dayjs().add(3, 'day').format('YYYY-MM-DDTHH:mm:ss.SSS[Z]');
                exercise.sampleSolutionModel = null;
                courseManagementRequests.createModelingExercise(exercise, { course: testCourse }).then((resp) => {
                    modelingExercise = resp.body;
                });
            });
        });

        it('Student can not see unreleased Modeling Exercise', () => {
            cy.login(student, '/courses');
            cy.get('.card-body').contains(testCourse.title).click({ force: true });
            cy.get('.col-lg-8').should('contain.text', 'No exercises available for the course.');
        });

        it('Release a Modeling Exercise', () => {
            cy.login(instructor, `/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/edit`);
            createModelingExercise.setReleaseDate(dayjs().subtract(1, 'hour').toString());
            createModelingExercise.save();
        });

        it('Student can start and submit their model', () => {
            cy.intercept('/api/courses/*/exercises/*/participations').as('createModelingParticipation');
            cy.login(student, `/courses/${testCourse.id}`);
            cy.get('.col-lg-8').contains(`Cypress Modeling Exercise ${uid}`).click();
            cy.get('jhi-exercise-details-student-actions.col > >').contains('Start exercise').click();
            cy.wait('@createModelingParticipation');
            cy.get('.btn').should('contain.text', 'Open modelling editor');
            cy.get('.btn').click();
            modelingEditor.addComponentToModel(1);
            modelingEditor.addComponentToModel(2);
            modelingEditor.addComponentToModel(3);
            cy.get('.jhi-btn').click();
            cy.get('.alerts').should('contain.text', 'Your submission was successful! You can change your submission or wait for your feedback.');
            cy.get('.col-auto').should('contain.text', 'No graded result');
        });

        it('Close exercise for submissions', () => {
            cy.login(instructor, `/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/edit`);
            createModelingExercise.setDueDate(dayjs().add(1, 'second').toString());
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
            createModelingExercise.setAssessmentDueDate(dayjs().toString());
            createModelingExercise.save();
        });

        it('Student can view the assessment and complain', () => {
            cy.intercept('POST', '/api/complaints').as('complaintCreated');
            cy.login(student, `/courses/${testCourse.id}/exercises/${modelingExercise.id}`);
            cy.get('jhi-submission-result-status > .col-auto').should('contain.text', 'Score').and('contain.text', '2 of 10 points');
            cy.get('jhi-exercise-details-student-actions.col > > :nth-child(2)').click();
            cy.url().should('contain', `/courses/${testCourse.id}/modeling-exercises/${modelingExercise.id}/participate/`);
            cy.get('.col-xl-8').should('contain.text', 'thanks, i hate it');
            cy.get('jhi-complaint-interactions > :nth-child(1) > .mt-4 > :nth-child(1)').click();
            cy.get('#complainTextArea').type('Thanks i hate you :^)');
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
