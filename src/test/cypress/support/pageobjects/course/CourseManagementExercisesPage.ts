import { Exercise } from 'app/entities/exercise.model';

import { DELETE, MODELING_EXERCISE_BASE, PROGRAMMING_EXERCISE_BASE, QUIZ_EXERCISE_BASE, TEXT_EXERCISE_BASE, UPLOAD_EXERCISE_BASE } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the course management exercises page.
 */
export class CourseManagementExercisesPage {
    getExercise(exerciseID: number) {
        return cy.get(`#exercise-card-${exerciseID}`);
    }

    clickDeleteExercise(exerciseID: number) {
        this.getExercise(exerciseID).find('#delete-exercise').click();
    }

    clickExampleSubmissionsButton() {
        cy.get('#example-submissions-button').click();
    }

    getExerciseTitle() {
        return cy.contains('Title').parent().parent().find('dd');
    }

    deleteTextExercise(exercise: Exercise) {
        this.getExercise(exercise.id!).find('#delete-exercise').click();
        cy.get('#confirm-entity-name').type(exercise.title!);
        cy.intercept(DELETE, `${TEXT_EXERCISE_BASE}/*`).as('deleteTextExercise');
        cy.get('#delete').click();
        cy.wait('@deleteTextExercise');
    }

    deleteModelingExercise(exercise: Exercise) {
        this.getExercise(exercise.id!).find('#delete-exercise').click();
        cy.get('#confirm-entity-name').type(exercise.title!);
        cy.intercept(DELETE, `${MODELING_EXERCISE_BASE}/*`).as('deleteModelingExercise');
        cy.get('#delete').click();
        cy.wait('@deleteModelingExercise');
    }

    deleteQuizExercise(exercise: Exercise) {
        this.getExercise(exercise.id!).find(`#delete-quiz-${exercise.id}`).click();
        cy.get('#confirm-entity-name').type(exercise.title!);
        cy.intercept(DELETE, `${QUIZ_EXERCISE_BASE}/*`).as('deleteQuizExercise');
        cy.get('#delete').click();
        cy.wait('@deleteQuizExercise');
    }

    deleteProgrammingExercise(exercise: Exercise) {
        this.getExercise(exercise.id!).find('#delete-exercise').click();
        cy.get('#additional-check-0').check();
        cy.get('#additional-check-1').check();
        cy.get('#confirm-entity-name').type(exercise.title!);
        cy.intercept(DELETE, `${PROGRAMMING_EXERCISE_BASE}/*`).as('deleteProgrammingExercise');
        cy.get('#delete').click();
        cy.wait('@deleteProgrammingExercise');
    }

    deleteFileUploadExercise(exercise: Exercise) {
        this.getExercise(exercise.id!).find('#delete-exercise').click();
        cy.get('#confirm-entity-name').type(exercise.title!);
        cy.intercept(DELETE, `${UPLOAD_EXERCISE_BASE}/*`).as('deleteFileUploadExercise');
        cy.get('#delete').click();
        cy.wait('@deleteFileUploadExercise');
    }

    createProgrammingExercise() {
        cy.get('#create-programming-exercise').click();
    }

    createModelingExercise() {
        cy.get('#create-modeling-exercise').click();
    }

    createTextExercise() {
        cy.get('#create-text-exercise').click();
    }

    createQuizExercise() {
        cy.get('#create-quiz-button').click();
    }

    createFileUploadExercise() {
        cy.get('#create-file-upload-exercise').click();
    }

    importProgrammingExercise() {
        cy.get('#import-programming-exercise').click();
    }

    importModelingExercise() {
        cy.get('#import-modeling-exercise').click();
    }

    importTextExercise() {
        cy.get('#import-text-exercise').click();
    }

    importQuizExercise() {
        cy.get('#import-quiz-exercise').click();
    }

    clickImportExercise(exerciseID: number) {
        return cy.get(`.exercise-${exerciseID}`).find('.import').click();
    }

    startQuiz(quizID: number) {
        cy.get(`#instructor-quiz-start-${quizID}`).click();
    }

    shouldContainExerciseWithName(exerciseID: number) {
        this.getExercise(exerciseID).scrollIntoView().should('be.visible');
    }

    getModelingExerciseTitle(exerciseID: number) {
        return cy.get(`#exercise-card-${exerciseID}`).find(`#modeling-exercise-${exerciseID}-title`);
    }

    getModelingExerciseMaxPoints(exerciseID: number) {
        return cy.get(`#exercise-card-${exerciseID}`).find(`#modeling-exercise-${exerciseID}-maxPoints`);
    }
}
