import dayjs from 'dayjs/esm';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Course } from 'app/entities/course.model';
import { MODELING_EDITOR_CANVAS } from '../../../support/pageobjects/exercises/modeling/ModelingEditor';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { courseManagementExercises, courseManagementRequest, modelingExerciseAssessment, modelingExerciseCreation, modelingExerciseEditor } from '../../../support/artemis';
import { admin, instructor, studentOne } from '../../../support/users';

// Common primitives
let course: Course;
let modelingExercise: ModelingExercise;
const modelingExerciseTitle = 'Cypress modeling exercise';

describe('Modeling Exercise Management Spec', () => {
    before('Create a course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response: Cypress.Response<Course>) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequest.addInstructorToCourse(course, instructor);
            courseManagementRequest.addStudentToCourse(course, studentOne);
        });
    });

    after('Delete the test course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id!);
    });

    afterEach('Delete modeling exercise', () => {
        cy.login(instructor);
        courseManagementRequest.deleteModelingExercise(modelingExercise.id!);
    });

    describe('Create Modeling Exercise', () => {
        beforeEach('Login as instructor', () => {
            cy.login(instructor);
        });

        it('Create a new modeling exercise', () => {
            cy.visit(`/course-management/${course.id}/exercises`);
            courseManagementExercises.createModelingExercise();
            modelingExerciseCreation.setTitle(modelingExerciseTitle);
            modelingExerciseCreation.addCategories(['e2e-testing', 'test2']);
            modelingExerciseCreation.setPoints(10);
            modelingExerciseCreation
                .save()
                .its('response.body')
                .then((body: any) => {
                    modelingExercise = body;
                    cy.contains(modelingExercise.title!).should('exist');
                    cy.log('Create Example Solution');
                    cy.visit(`/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/edit`);
                    modelingExerciseEditor.addComponentToExampleSolutionModel(1);
                    modelingExerciseCreation.save();
                    cy.get(MODELING_EDITOR_CANVAS).children().eq(0).should('exist');

                    cy.log('Create Example Submission');
                    cy.visit(`/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/example-submissions`);
                    modelingExerciseEditor.clickCreateExampleSubmission();
                    modelingExerciseEditor.addComponentToExampleSolutionModel(1);
                    modelingExerciseEditor.addComponentToExampleSolutionModel(2);
                    modelingExerciseEditor.addComponentToExampleSolutionModel(3);
                    modelingExerciseEditor.clickCreateNewExampleSubmission();
                    modelingExerciseEditor.showExampleAssessment();
                    modelingExerciseAssessment.openAssessmentForComponent(1);
                    modelingExerciseAssessment.assessComponent(-1, 'False');
                    modelingExerciseAssessment.clickNextAssessment();
                    modelingExerciseAssessment.assessComponent(2, 'Good');
                    modelingExerciseAssessment.clickNextAssessment();
                    modelingExerciseAssessment.assessComponent(0, 'Unnecessary');
                    modelingExerciseAssessment.submitExample();
                    cy.visit(`/course-management/${course.id}/modeling-exercises/${modelingExercise.id}`);
                    cy.get('#modeling-editor-canvas').should('exist');
                });
        });
    });

    describe('Edit Modeling Exercise', () => {
        beforeEach('Create Modeling Exercise', () => {
            cy.login(admin);
            courseManagementRequest.createModelingExercise({ course }).then((resp) => {
                modelingExercise = resp.body;
            });
        });

        it('Edit Existing Modeling Exercise', () => {
            cy.visit(`/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/edit`);
            const newTitle = 'Cypress EDITED ME';
            const points = 100;
            modelingExerciseCreation.setTitle(newTitle);
            modelingExerciseCreation.pickDifficulty({ hard: true });
            modelingExerciseCreation.setReleaseDate(dayjs().add(1, 'day'));
            modelingExerciseCreation.setDueDate(dayjs().add(2, 'day'));
            modelingExerciseCreation.setAssessmentDueDate(dayjs().add(3, 'day'));
            modelingExerciseCreation.includeInOverallScore();
            modelingExerciseCreation.setPoints(points);
            modelingExerciseCreation.save();
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
            courseManagementRequest.createModelingExercise({ course }, modelingExerciseTitle, dayjs().add(1, 'hour')).then((resp) => {
                modelingExercise = resp.body;
            });
            cy.login(studentOne, '/courses');
            cy.contains(course.title!).click({ force: true });
            cy.contains('No exercises available for the course.').should('be.visible');
        });

        it('Student can see released Modeling Exercise', () => {
            courseManagementRequest.createModelingExercise({ course }, modelingExerciseTitle, dayjs().subtract(1, 'hour')).then((resp) => {
                modelingExercise = resp.body;
            });
            cy.login(studentOne, '/courses');
            cy.visit('/courses/' + course.id + '/exercises/' + modelingExercise.id);
        });
    });
});
