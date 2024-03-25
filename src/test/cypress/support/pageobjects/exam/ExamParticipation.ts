import { Interception } from 'cypress/types/net-stubbing';

import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

import {
    courseList,
    courseOverview,
    examNavigation,
    examStartEnd,
    modelingExerciseEditor,
    programmingExerciseEditor,
    quizExerciseMultipleChoice,
    textExerciseEditor,
} from '../../artemis';
import { AdditionalData, ExerciseType } from '../../constants';
import { CypressCredentials } from '../../users';
import { getExercise } from '../../utils';
import { ProgrammingExerciseSubmission } from '../exercises/programming/OnlineEditorPage';

/**
 * A class which encapsulates UI selectors and actions for the exam details page.
 */
export class ExamParticipation {
    /**
     * Makes a submission in a provided exercise
     * @param exerciseID the id of the exercise
     * @param exerciseType the type of the exercise
     * @param additionalData additional data such as the expected score
     */
    makeSubmission(exerciseID: number, exerciseType: ExerciseType, additionalData?: AdditionalData) {
        switch (exerciseType) {
            case ExerciseType.TEXT:
                this.makeTextExerciseSubmission(exerciseID, additionalData!.textFixture!);
                break;
            case ExerciseType.MODELING:
                this.makeModelingExerciseSubmission(exerciseID);
                break;
            case ExerciseType.QUIZ:
                this.makeQuizExerciseSubmission(exerciseID, additionalData!.quizExerciseID!);
                break;
            case ExerciseType.PROGRAMMING:
                this.makeProgrammingExerciseSubmission(exerciseID, additionalData!.submission!, additionalData!.practiceMode);
                break;
        }
    }

    makeTextExerciseSubmission(exerciseID: number, textFixture: string) {
        cy.fixture(textFixture).then((submissionText) => {
            textExerciseEditor.typeSubmission(exerciseID, submissionText);
        });
        cy.wait(1000);
    }

    private makeProgrammingExerciseSubmission(exerciseID: number, submission: ProgrammingExerciseSubmission, practiceMode = false) {
        programmingExerciseEditor.toggleCompressFileTree(exerciseID);
        programmingExerciseEditor.deleteFile(exerciseID, 'Client.java');
        programmingExerciseEditor.deleteFile(exerciseID, 'BubbleSort.java');
        programmingExerciseEditor.deleteFile(exerciseID, 'MergeSort.java');
        programmingExerciseEditor.typeSubmission(exerciseID, submission);
        if (practiceMode) {
            programmingExerciseEditor.submitPractice(exerciseID);
        } else {
            programmingExerciseEditor.submit(exerciseID);
        }
        programmingExerciseEditor.getResultScoreFromExercise(exerciseID).contains(submission.expectedResult).and('be.visible');
    }

    makeModelingExerciseSubmission(exerciseID: number) {
        modelingExerciseEditor.addComponentToModel(exerciseID, 1, false);
        modelingExerciseEditor.addComponentToModel(exerciseID, 2, false);
        modelingExerciseEditor.addComponentToModel(exerciseID, 3, false);
    }

    makeQuizExerciseSubmission(exerciseID: number, quizExerciseID: number) {
        quizExerciseMultipleChoice.tickAnswerOption(exerciseID, 0, quizExerciseID);
        quizExerciseMultipleChoice.tickAnswerOption(exerciseID, 2, quizExerciseID);
    }

    openExam(student: CypressCredentials, course: Course, exam: Exam) {
        cy.login(student, '/');
        cy.visit('/courses');
        courseList.openCourse(course.id!);
        courseOverview.openExamsTab();
        courseOverview.openExam(exam.id!);
        cy.url().should('contain', `/exams/${exam.id}`);
    }

    startParticipation(student: CypressCredentials, course: Course, exam: Exam) {
        this.openExam(student, course, exam);
        examStartEnd.startExam(true);
    }

    selectExerciseOnOverview(index: number) {
        cy.get(`.exercise-table tr:nth-child(${index}) a`).click();
    }

    clickSaveAndContinue() {
        cy.get('#save').click();
    }

    checkExerciseTitle(exerciseID: number, title: string) {
        getExercise(exerciseID).find('.exercise-title').contains(title);
    }

    checkExamTitle(title: string) {
        cy.get('#exam-title').contains(title);
    }

    getResultScore() {
        cy.reloadUntilFound('#exercise-result-score');
        return cy.get('#exercise-result-score');
    }

    checkExamFinishedTitle(title: string) {
        cy.get('#exam-finished-title').contains(title, { timeout: 40000 });
    }

    checkExamFullnameInputExists() {
        cy.get('#fullname', { timeout: 20000 }).should('exist');
    }

    checkYourFullname(name: string) {
        cy.get('#your-name', { timeout: 20000 }).contains(name);
    }

    handInEarly() {
        examNavigation.handInEarly();
        examStartEnd.finishExam().then((request: Interception) => {
            expect(request.response!.statusCode).to.eq(200);
        });
    }

    verifyExerciseTitleOnFinalPage(exerciseID: number, exerciseTitle: string) {
        getExercise(exerciseID).find(`#exercise-group-title-${exerciseID}`).scrollIntoView();
        cy.get(`#exercise-group-title-${exerciseID}`).contains(exerciseTitle).should('be.visible');
    }

    verifyTextExerciseOnFinalPage(textFixture: string) {
        cy.fixture(textFixture).then((submissionText) => {
            cy.get('textarea').should('have.value', submissionText);
        });
    }
}
