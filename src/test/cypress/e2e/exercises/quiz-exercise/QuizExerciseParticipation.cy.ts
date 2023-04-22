import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import multipleChoiceQuizTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerQuizTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import {
    courseManagementRequest,
    courseOverview,
    quizExerciseCreation,
    quizExerciseDragAndDropQuiz,
    quizExerciseMultipleChoice,
    quizExerciseShortAnswerQuiz,
} from '../../../support/artemis';
import { admin, studentOne } from '../../../support/users';

// Common primitives
let course: Course;
let quizExercise: QuizExercise;

describe('Quiz Exercise Participation', () => {
    before('Set up course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
        });
    });

    after('Delete Course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id!);
    });

    describe('Quiz exercise participation', () => {
        beforeEach('Create quiz exercise', () => {
            cy.login(admin);
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate]).then((quizResponse) => {
                quizExercise = quizResponse.body;
            });
        });

        it('Student cannot see hidden quiz', () => {
            cy.login(studentOne, '/courses/' + course.id);
            cy.contains('No exercises available for the course.').should('be.visible');
        });

        it('Student can see a visible quiz', () => {
            cy.login(admin);
            courseManagementRequest.setQuizVisible(quizExercise.id!);
            cy.login(studentOne, '/courses/' + course.id);
            courseOverview.openRunningExercise(quizExercise.id!);
        });

        it('Student can participate in MC quiz', () => {
            cy.login(admin);
            courseManagementRequest.setQuizVisible(quizExercise.id!);
            courseManagementRequest.startQuizNow(quizExercise.id!);
            cy.login(studentOne, '/courses/' + course.id);
            courseOverview.startExercise(quizExercise.id!);
            quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 0);
            quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 2);
            quizExerciseMultipleChoice.submit();
        });
    });

    describe('SA quiz participation', () => {
        before('Create SA quiz', () => {
            cy.login(admin);
            courseManagementRequest.createQuizExercise({ course }, [shortAnswerQuizTemplate]).then((quizResponse) => {
                quizExercise = quizResponse.body;
                courseManagementRequest.setQuizVisible(quizExercise.id!);
                courseManagementRequest.startQuizNow(quizExercise.id!);
            });
        });

        it('Student can participate in SA quiz', () => {
            const quizQuestionId = quizExercise.quizQuestions![0].id!;
            cy.login(studentOne, '/courses/' + course.id);
            courseOverview.startExercise(quizExercise.id!);
            quizExerciseShortAnswerQuiz.typeAnswer(0, 1, quizQuestionId, 'give');
            quizExerciseShortAnswerQuiz.typeAnswer(1, 1, quizQuestionId, 'let');
            quizExerciseShortAnswerQuiz.typeAnswer(2, 1, quizQuestionId, 'run');
            quizExerciseShortAnswerQuiz.typeAnswer(2, 3, quizQuestionId, 'desert');
            quizExerciseShortAnswerQuiz.typeAnswer(3, 1, quizQuestionId, 'cry');
            quizExerciseShortAnswerQuiz.typeAnswer(4, 1, quizQuestionId, 'goodbye');
            quizExerciseShortAnswerQuiz.submit();
        });
    });

    // TODO: Fix the drag and drop
    // describe.skip('DnD Quiz participation', () => {
    //     before('Create DND quiz', () => {
    //         cy.login(admin, '/course-management/' + course.id + '/exercises');
    //         cy.get('#create-quiz-button').should('be.visible').click();
    //         quizExerciseCreation.setTitle('Cypress Quiz');
    //         quizExerciseCreation.addDragAndDropQuestion('DnD Quiz');
    //         quizExerciseCreation.saveQuiz().then((quizResponse) => {
    //             quizExercise = quizResponse.response?.body;
    //             courseManagementRequest.setQuizVisible(quizExercise.id!);
    //             courseManagementRequest.startQuizNow(quizExercise.id!);
    //         });
    //     });

    //     it('Student can participate in DnD Quiz', () => {
    //         cy.login(studentOne, '/courses/' + course.id);
    //         courseOverview.startExercise(quizExercise.id!);
    //         quizExerciseDragAndDropQuiz.dragItemIntoDragArea(0);
    //         quizExerciseDragAndDropQuiz.submit();
    //     });
    // });
});
