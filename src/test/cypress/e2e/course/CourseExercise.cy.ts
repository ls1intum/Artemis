import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import { courseManagementAPIRequest, courseOverview, exerciseAPIRequest } from '../../support/artemis';
import { admin } from '../../support/users';
import { convertModelAfterMultiPart } from '../../support/utils';

describe('Course Exercise', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
        });
    });

    describe('Search Exercise', () => {
        let exercise1: QuizExercise;
        let exercise2: QuizExercise;
        let exercise3: QuizExercise;

        before('Create Exercises', () => {
            exerciseAPIRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise Quiz 1').then((response) => {
                exercise1 = convertModelAfterMultiPart(response);
            });
            exerciseAPIRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise Quiz 2').then((response) => {
                exercise2 = convertModelAfterMultiPart(response);
            });
            exerciseAPIRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise 3').then((response) => {
                exercise3 = convertModelAfterMultiPart(response);
            });
        });

        it('should filter exercises based on title', () => {
            cy.visit(`/courses/${course.id}/exercises`);
            courseOverview.getExercise(exercise1.title!).should('be.visible');
            courseOverview.getExercise(exercise2.title!).should('be.visible');
            courseOverview.getExercise(exercise3.title!).should('be.visible');
            courseOverview.search('Course Exercise Quiz');
            courseOverview.getExercise(exercise1.title!).should('be.visible');
            courseOverview.getExercise(exercise2.title!).should('be.visible');
            courseOverview.getExercise(exercise3.title!).should('not.exist');
        });

        after('Delete Exercises', () => {
            exerciseAPIRequest.deleteQuizExercise(exercise1.id!);
            exerciseAPIRequest.deleteQuizExercise(exercise2.id!);
            exerciseAPIRequest.deleteQuizExercise(exercise3.id!);
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
