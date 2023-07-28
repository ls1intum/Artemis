import { Interception } from 'cypress/types/net-stubbing';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { courseManagement, courseManagementExercises, courseManagementRequest, navigationBar, programmingExerciseCreation } from '../../../support/artemis';
import { generateUUID } from '../../../support/utils';
import { convertModelAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { admin } from '../../../support/users';

describe('Programming Exercise Management', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertModelAfterMultiPart(response);
        });
    });

    describe('Programming exercise creation', () => {
        it('Creates a new programming exercise', () => {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagement.openExercisesOfCourse(course.id!);
            courseManagementExercises.createProgrammingExercise();
            cy.url().should('include', '/programming-exercises/new');
            cy.log('Filling out programming exercise info...');
            const exerciseTitle = 'Programming exercise ' + generateUUID();
            programmingExerciseCreation.setTitle(exerciseTitle);
            programmingExerciseCreation.setShortName('programming' + generateUUID());
            programmingExerciseCreation.setPackageName('de.test');
            programmingExerciseCreation.setPoints(100);
            programmingExerciseCreation.checkAllowOnlineEditor();
            programmingExerciseCreation.generate().then((request: Interception) => {
                const exercise = request.response!.body;
                courseManagementExercises.getExerciseTitle().should('contain.text', exerciseTitle);
                cy.url().should('include', `/programming-exercises/${exercise.id}`);
            });
        });
    });

    describe('Programming exercise deletion', () => {
        let exercise: ProgrammingExercise;

        before(() => {
            cy.login(admin, '/');
            courseManagementRequest.createProgrammingExercise({ course }).then((response) => {
                exercise = response.body;
            });
        });

        it('Deletes an existing programming exercise', () => {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagement.openExercisesOfCourse(course.id!);
            courseManagementExercises.deleteProgrammingExercise(exercise);
            courseManagementExercises.getExercise(exercise.id!).should('not.exist');
        });
    });

    after('Delete course', () => {
        courseManagementRequest.deleteCourse(course, admin);
    });
});
