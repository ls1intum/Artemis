import { Course } from '../../../../main/webapp/app/entities/course.model';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import { courseManagementRequest, courseOverview } from '../../support/artemis';
import { convertModelAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { admin } from '../../support/users';
import { QuizExercise } from '../../../../main/webapp/app/entities/quiz/quiz-exercise.model';

describe('Course Exercise', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
        });
    });

    describe('Search Exercise', () => {
        let exercise1: QuizExercise;
        let exercise2: QuizExercise;
        let exercise3: QuizExercise;

        before('Create Exercises', () => {
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise Quiz 1').then((response) => {
                exercise1 = response.body;
            });
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise Quiz 2').then((response) => {
                exercise2 = response.body;
            });
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise 3').then((response) => {
                exercise3 = response.body;
            });
        });

        it('should filter exercises based on title', () => {
            cy.visit(`/courses/${course.id}/exercises`);
            courseOverview.getExercise(exercise1.id!).should('be.visible');
            courseOverview.getExercise(exercise2.id!).should('be.visible');
            courseOverview.getExercise(exercise3.id!).should('be.visible');
            courseOverview.search('Course Exercise Quiz');
            courseOverview.getExercise(exercise1.id!).should('be.visible');
            courseOverview.getExercise(exercise2.id!).should('be.visible');
            courseOverview.getExercise(exercise3.id!).should('not.exist');
        });

        after('Delete Exercises', () => {
            courseManagementRequest.deleteQuizExercise(exercise1.id!);
            courseManagementRequest.deleteQuizExercise(exercise2.id!);
            courseManagementRequest.deleteQuizExercise(exercise3.id!);
        });
    });

    after('Delete course', () => {
        courseManagementRequest.deleteCourse(course, admin);
    });
});
