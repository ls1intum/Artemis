import dayjs from 'dayjs/esm';
import { MODELING_EDITOR_CANVAS } from 'src/test/cypress/support/constants';

import { Course } from 'app/entities/course.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';

import {
    courseManagement,
    courseManagementAPIRequest,
    courseManagementExercises,
    courseOverview,
    exerciseAPIRequest,
    modelingExerciseAssessment,
    modelingExerciseCreation,
    modelingExerciseEditor,
    navigationBar,
} from '../../../support/artemis';
import { admin, instructor, studentOne } from '../../../support/users';
import { convertModelAfterMultiPart, generateUUID } from '../../../support/utils';

describe('Modeling Exercise Management', () => {
    let course: Course;
    let modelingExercise: ModelingExercise;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response: Cypress.Response<Course>) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addInstructorToCourse(course, instructor);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
        });
    });

    describe('Create Modeling Exercise', () => {
        it('Create a new modeling exercise', { scrollBehavior: 'center' }, () => {
            cy.login(instructor);
            cy.visit(`/course-management/${course.id}/exercises`);
            courseManagementExercises.createModelingExercise();
            modelingExerciseCreation.setTitle('Modeling ' + generateUUID());
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
                    modelingExerciseEditor.getModelingCanvas().should('exist');
                });
        });

        after('Delete modeling exercise', () => {
            if (modelingExercise) {
                cy.login(admin);
                exerciseAPIRequest.deleteModelingExercise(modelingExercise.id!);
            }
        });
    });

    describe('Edit Modeling Exercise', () => {
        before('Create Modeling Exercise', () => {
            cy.login(admin);
            exerciseAPIRequest.createModelingExercise({ course }).then((resp) => {
                modelingExercise = resp.body;
            });
        });

        it('Edit Existing Modeling Exercise', { scrollBehavior: 'center' }, () => {
            cy.visit(`/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/edit`);
            const newTitle = 'New Modeling Exercise Title';
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
            courseManagementExercises.getModelingExerciseTitle(modelingExercise.id!).contains(newTitle);
            courseManagementExercises.getModelingExerciseMaxPoints(modelingExercise.id!).contains(points.toString());
        });

        after('Delete exercise', () => {
            exerciseAPIRequest.deleteModelingExercise(modelingExercise.id!);
        });
    });

    describe('Delete Modeling Exercise', () => {
        let modelingExercise: ModelingExercise;

        before('Create Modeling exercise', () => {
            cy.login(admin, '/');
            exerciseAPIRequest.createModelingExercise({ course }).then((resp) => {
                modelingExercise = resp.body;
            });
        });

        it('Deletes an existing Modeling exercise', () => {
            cy.login(instructor, '/');
            navigationBar.openCourseManagement();
            courseManagement.openExercisesOfCourse(course.id!);
            courseManagementExercises.deleteModelingExercise(modelingExercise);
            courseManagementExercises.getExercise(modelingExercise.id!).should('not.exist');
        });
    });

    describe('Modeling Exercise Release', () => {
        it('Student can not see unreleased Modeling Exercise', () => {
            cy.login(instructor);
            exerciseAPIRequest.createModelingExercise({ course }, 'Modeling ' + generateUUID(), dayjs().add(1, 'hour')).then((resp) => {
                modelingExercise = resp.body;
            });
            cy.login(studentOne, '/courses');
            cy.contains(course.title!).click({ force: true });
            courseOverview.getExercises().should('have.length', 0);
        });

        it('Student can see released Modeling Exercise', () => {
            cy.login(instructor);
            exerciseAPIRequest.createModelingExercise({ course }, 'Modeling ' + generateUUID(), dayjs().subtract(1, 'hour')).then((resp) => {
                modelingExercise = resp.body;
            });
            cy.login(studentOne, '/courses');
            cy.visit('/courses/' + course.id + '/exercises/' + modelingExercise.id);
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
