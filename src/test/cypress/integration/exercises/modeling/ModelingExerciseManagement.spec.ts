import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Course } from 'app/entities/course.model';
import { dayjsToString } from '../../../support/utils';
import { artemis } from '../../../support/ArtemisTesting';
import { MODELING_EDITOR_CANVAS } from '../../../support/pageobjects/exercises/modeling/ModelingEditor';

// https://day.js.org/docs is a tool for date/time
import dayjs from 'dayjs/esm';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';

// pageobjects
const createModelingExercise = artemis.pageobjects.exercise.modeling.creation;
const modelingExerciseExampleSubmission = artemis.pageobjects.assessment.modeling;
const modelingEditor = artemis.pageobjects.exercise.modeling.editor;
// requests
const courseManagementRequests = artemis.requests.courseManagement;
// Users
const userManagement = artemis.users;
const admin = userManagement.getAdmin();
const student = userManagement.getStudentOne();
const instructor = userManagement.getInstructor();

let course: Course;
let modelingExercise: ModelingExercise;

const modelingExerciseTitle = 'Cypress modeling exercise';

describe('Modeling Exercise Management Spec', () => {
    before('Create a course', () => {
        cy.login(admin);
        courseManagementRequests.createCourse().then((response: Cypress.Response<Course>) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequests.addInstructorToCourse(course, instructor);
            courseManagementRequests.addStudentToCourse(course, student);
        });
    });

    after('Delete the test course', () => {
        cy.login(admin);
        courseManagementRequests.deleteCourse(course.id!);
    });

    afterEach('Delete modeling exercise', () => {
        cy.login(instructor);
        courseManagementRequests.deleteModelingExercise(modelingExercise.id!);
    });

    describe('Create Modeling Exercise', () => {
        beforeEach('Login as instructor', () => {
            cy.login(instructor);
        });

        it('Create a new modeling exercise', () => {
            cy.visit(`/course-management/${course.id}/exercises`);
            cy.get('#modeling-exercise-create-button').click();
            createModelingExercise.setTitle(modelingExerciseTitle);
            createModelingExercise.addCategories(['e2e-testing', 'test2']);
            createModelingExercise.setPoints(10);
            createModelingExercise
                .save()
                .its('response.body')
                .then((body: any) => {
                    modelingExercise = body;
                    cy.contains(modelingExercise.title!).should('exist');
                    cy.log('Create Example Solution');
                    cy.visit(`/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/edit`);
                    modelingEditor.addComponentToModel(1);
                    createModelingExercise.save();
                    cy.get(`${MODELING_EDITOR_CANVAS}`).children().eq(0).should('exist');

                    cy.log('Create Example Submission');
                    cy.visit(`/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/example-submissions`);
                    modelingEditor.clickCreateExampleSubmission();
                    modelingEditor.addComponentToModel(1);
                    modelingEditor.addComponentToModel(2);
                    modelingEditor.addComponentToModel(3);
                    modelingEditor.clickCreateNewExampleSubmission();
                    modelingEditor.showExampleAssessment();
                    modelingExerciseExampleSubmission.openAssessmentForComponent(1);
                    modelingExerciseExampleSubmission.assessComponent(-1, 'False');
                    modelingExerciseExampleSubmission.clickNextAssessment();
                    modelingExerciseExampleSubmission.assessComponent(2, 'Good');
                    modelingExerciseExampleSubmission.clickNextAssessment();
                    modelingExerciseExampleSubmission.assessComponent(0, 'Unnecessary');
                    modelingExerciseExampleSubmission.submitExample();
                    cy.visit(`/course-management/${course.id}/modeling-exercises/${modelingExercise.id}`);
                    cy.get('#modeling-editor-canvas').should('exist');
                });
        });
    });

    describe('Edit Modeling Exercise', () => {
        beforeEach('Create Modeling Exercise', () => {
            cy.login(admin);
            courseManagementRequests.createModelingExercise({ course }).then((resp) => {
                modelingExercise = resp.body;
            });
        });

        it('Edit Existing Modeling Exercise', () => {
            cy.visit(`/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/edit`);
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
            cy.visit(`/course-management/${course.id}/exercises`);
            cy.get('#exercise-card-' + modelingExercise.id)
                .find('#modeling-exercise-' + modelingExercise.id + '-title')
                .should('contain.text', newTitle);
            cy.get('#exercise-card-' + modelingExercise.id)
                .find('#modeling-exercise-' + modelingExercise.id + '-maxPoints')
                .should('contain.text', points.toString());
        });
    });

    describe('Modeling Exercise Release', () => {
        beforeEach('Login intructor', () => {
            cy.login(instructor);
        });

        it('Student can not see unreleased Modeling Exercise', () => {
            courseManagementRequests.createModelingExercise({ course }, modelingExerciseTitle, dayjs().add(1, 'hour')).then((resp) => {
                modelingExercise = resp.body;
            });
            cy.login(student, '/courses');
            cy.contains(course.title!).click({ force: true });
            cy.contains('No exercises available for the course.').should('be.visible');
        });

        it('Student can see released Modeling Exercise', () => {
            courseManagementRequests.createModelingExercise({ course }, modelingExerciseTitle, dayjs().subtract(1, 'hour')).then((resp) => {
                modelingExercise = resp.body;
            });
            cy.login(student, '/courses');
            cy.visit('/courses/' + course.id + '/exercises/' + modelingExercise.id);
        });
    });
});
