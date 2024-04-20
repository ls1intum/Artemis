import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import multipleChoiceQuizTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerQuizTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import { courseManagementAPIRequest, courseOverview, exerciseAPIRequest, quizExerciseMultipleChoice, quizExerciseShortAnswerQuiz } from '../../../support/artemis';
import { admin, studentOne } from '../../../support/users';
import { convertModelAfterMultiPart } from '../../../support/utils';

describe('Quiz Exercise Participation', () => {
    let course: Course;
    let quizExercise: QuizExercise;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
        });
    });

    describe('Quiz exercise participation', () => {
        beforeEach('Create quiz exercise', () => {
            cy.login(admin);
            exerciseAPIRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate]).then((quizResponse) => {
                quizExercise = convertModelAfterMultiPart(quizResponse);
            });
        });

        it('Student cannot see hidden quiz', () => {
            cy.login(studentOne, '/courses/' + course.id);
            courseOverview.getExercises().should('have.length', 0);
        });

        it('Student can see a visible quiz', () => {
            cy.login(admin);
            exerciseAPIRequest.setQuizVisible(quizExercise.id!);
            cy.login(studentOne, '/courses/' + course.id);
            courseOverview.openRunningExercise(quizExercise.id!);
        });

        it('Student can participate in MC quiz', () => {
            cy.login(admin);
            exerciseAPIRequest.setQuizVisible(quizExercise.id!);
            exerciseAPIRequest.startQuizNow(quizExercise.id!);
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
            exerciseAPIRequest.createQuizExercise({ course }, [shortAnswerQuizTemplate]).then((quizResponse) => {
                quizExercise = convertModelAfterMultiPart(quizResponse);
                exerciseAPIRequest.setQuizVisible(quizExercise.id!);
                exerciseAPIRequest.startQuizNow(quizExercise.id!);
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
    //         courseManagementExercises.createQuizExercise();
    //         quizExerciseCreation.setTitle('Cypress Quiz');
    //         quizExerciseCreation.addDragAndDropQuestion('DnD Quiz');
    //         quizExerciseCreation.saveQuiz().then((quizResponse) => {
    //             quizExercise = quizResponse.response?.body;
    //             courseManagementAPIRequest.setQuizVisible(quizExercise.id!);
    //             courseManagementAPIRequest.startQuizNow(quizExercise.id!);
    //         });
    //     });

    //     it('Student can participate in DnD Quiz', () => {
    //         cy.login(studentOne, '/courses/' + course.id);
    //         courseOverview.startExercise(quizExercise.id!);
    //         quizExerciseDragAndDropQuiz.dragItemIntoDragArea(0);
    //         quizExerciseDragAndDropQuiz.submit();
    //     });
    // });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
