import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import multipleChoiceQuizTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerQuizTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import { courseManagementAPIRequest, exerciseAPIRequest, exerciseResult } from '../../../support/artemis';
import { admin, studentOne, tutor } from '../../../support/users';
import { convertModelAfterMultiPart } from '../../../support/utils';

describe('Quiz Exercise Assessment', () => {
    let course: Course;
    let quizExercise: QuizExercise;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
            courseManagementAPIRequest.addTutorToCourse(course, tutor);
        });
    });

    describe(
        'MC Quiz assessment',
        {
            retries: 2,
        },
        () => {
            it('Assesses a mc quiz submission automatically', () => {
                cy.login(admin);
                exerciseAPIRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], undefined, undefined, 10).then((quizResponse) => {
                    quizExercise = convertModelAfterMultiPart(quizResponse);
                    exerciseAPIRequest.setQuizVisible(quizExercise.id!);
                    exerciseAPIRequest.startQuizNow(quizExercise.id!);
                    cy.login(studentOne);
                    exerciseAPIRequest.startExerciseParticipation(quizExercise.id!);
                    exerciseAPIRequest.createMultipleChoiceSubmission(quizExercise, [0, 2]);
                    cy.visit('/courses/' + course.id + '/exercises/' + quizExercise.id);
                    exerciseResult.shouldShowScore(50);
                });
            });
        },
    );

    describe(
        'SA Quiz assessment',
        {
            retries: 2,
        },
        () => {
            it('Assesses a sa quiz submission automatically', () => {
                cy.login(admin);
                exerciseAPIRequest.createQuizExercise({ course }, [shortAnswerQuizTemplate], undefined, undefined, 10).then((quizResponse) => {
                    quizExercise = convertModelAfterMultiPart(quizResponse);
                    exerciseAPIRequest.setQuizVisible(quizExercise.id!);
                    exerciseAPIRequest.startQuizNow(quizExercise.id!);
                    cy.login(studentOne);
                    exerciseAPIRequest.startExerciseParticipation(quizExercise.id!);
                    exerciseAPIRequest.createShortAnswerSubmission(quizExercise, ['give', 'let', 'run', 'desert']);
                    cy.visit('/courses/' + course.id + '/exercises/' + quizExercise.id);
                    exerciseResult.shouldShowScore(66.7);
                });
            });
        },
    );

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
