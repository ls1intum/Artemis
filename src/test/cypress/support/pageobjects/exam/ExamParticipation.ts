import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import { EXERCISE_TYPE } from '../../constants';
import { getExercise } from '../../utils';
import { artemis } from '../../ArtemisTesting';
import { Interception } from 'cypress/types/net-stubbing';
import { CypressCredentials } from 'src/test/cypress/support/users';
import { ProgrammingExerciseSubmission } from '../exercises/programming/OnlineEditorPage';

/**
 * A class which encapsulates UI selectors and actions for the exam details page.
 */
export class ExamParticipation {
    /**
     * Makes a submission in a provided exercise
     * @param examTitle the exam title to confirm the deletion
     */
    makeSubmission(exerciseID: number, exerciseType: EXERCISE_TYPE, additionalData?: AdditionalData) {
        switch (exerciseType) {
            case EXERCISE_TYPE.Text:
                this.makeTextExerciseSubmission(exerciseID, additionalData!.textFixture!);
                break;
            case EXERCISE_TYPE.Modeling:
                this.makeModelingExerciseSubmission(exerciseID);
                break;
            case EXERCISE_TYPE.Quiz:
                this.makeQuizExerciseSubmission(exerciseID, additionalData!.quizExerciseID!);
                break;
            case EXERCISE_TYPE.Programming:
                this.makeProgrammingExerciseSubmission(exerciseID, additionalData!.submission!, additionalData!.expectedScore!);
                break;
        }
    }

    makeTextExerciseSubmission(exerciseID: number, textFixture: string) {
        const textEditor = artemis.pageobjects.exercise.text.editor;
        cy.fixture(textFixture).then((submissionText) => {
            textEditor.typeSubmission(exerciseID, submissionText);
        });
        cy.wait(1000);
    }

    private makeProgrammingExerciseSubmission(exerciseID: number, submission: ProgrammingExerciseSubmission, expectedScore: number) {
        const onlineEditor = artemis.pageobjects.exercise.programming.editor;
        onlineEditor.toggleCompressFileTree(exerciseID);
        onlineEditor.deleteFile(exerciseID, 'Client.java');
        onlineEditor.deleteFile(exerciseID, 'BubbleSort.java');
        onlineEditor.deleteFile(exerciseID, 'MergeSort.java');
        onlineEditor.typeSubmission(exerciseID, submission, 'de.test');
        onlineEditor.submit(exerciseID);
        onlineEditor.getResultScoreFromExercise(exerciseID).contains(`${expectedScore}%`).and('be.visible');
    }

    makeModelingExerciseSubmission(exerciseID: number) {
        const modelingEditor = artemis.pageobjects.exercise.modeling.editor;
        modelingEditor.addComponentToModel(exerciseID, 1, false);
        modelingEditor.addComponentToModel(exerciseID, 2, false);
        modelingEditor.addComponentToModel(exerciseID, 3, false);
    }

    makeQuizExerciseSubmission(exerciseID: number, quizExerciseID: number) {
        const multipleChoiceQuiz = artemis.pageobjects.exercise.quiz.multipleChoice;
        multipleChoiceQuiz.tickAnswerOption(exerciseID, 0, quizExerciseID);
        multipleChoiceQuiz.tickAnswerOption(exerciseID, 2, quizExerciseID);
    }

    startParticipation(student: CypressCredentials, course: Course, exam: Exam) {
        const courses = artemis.pageobjects.course.list;
        const courseOverview = artemis.pageobjects.course.overview;
        const examStartEnd = artemis.pageobjects.exam.startEnd;
        cy.login(student, '/');
        courses.openCourse(course.id!);
        courseOverview.openExamsTab();
        courseOverview.openExam(exam.id!);
        cy.url().should('contain', `/exams/${exam.id}`);
        examStartEnd.startExam();
    }

    openExercise(index: number) {
        const examNavigation = artemis.pageobjects.exam.navigationBar;
        examNavigation.openExerciseAtIndex(index);
    }

    checkExerciseTitle(exerciseID: number, title: string) {
        getExercise(exerciseID).find('.exercise-title').contains(title);
    }

    checkExamTitle(title: string) {
        cy.get('#exam-title').contains(title);
    }

    handInEarly() {
        const examNavigation = artemis.pageobjects.exam.navigationBar;
        const examStartEnd = artemis.pageobjects.exam.startEnd;
        examNavigation.handInEarly();
        examStartEnd.finishExam().then((request: Interception) => {
            expect(request.response!.statusCode).to.eq(200);
        });
    }

    verifyExerciseTitleOnFinalPage(exerciseID: number, exerciseTitle: string) {
        getExercise(exerciseID).find('.exercise-title').contains(exerciseTitle).should('be.visible');
    }

    verifyTextExerciseOnFinalPage(textFixture: string) {
        cy.fixture(textFixture).then((submissionText) => {
            cy.contains(submissionText).should('be.visible');
        });
    }
}

export class AdditionalData {
    quizExerciseID?: number;
    submission?: ProgrammingExerciseSubmission;
    expectedScore?: number;
    textFixture?: string;
}

export type Exercise = {
    title: string;
    type: EXERCISE_TYPE;
    id: number;
    additionalData?: AdditionalData;
};
