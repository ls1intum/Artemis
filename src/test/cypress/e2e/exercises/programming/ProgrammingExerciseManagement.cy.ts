import { Interception } from 'cypress/types/net-stubbing';

import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

import { courseManagement, courseManagementAPIRequest, courseManagementExercises, exerciseAPIRequest, navigationBar, programmingExerciseCreation } from '../../../support/artemis';
import { admin } from '../../../support/users';
import { convertModelAfterMultiPart, generateUUID } from '../../../support/utils';

describe('Programming Exercise Management', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse({ customizeGroups: true }).then((response) => {
            course = convertModelAfterMultiPart(response);
        });
    });

    describe('Programming exercise creation', () => {
        it('Creates a new programming exercise', { scrollBehavior: 'center' }, () => {
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
            exerciseAPIRequest.createProgrammingExercise({ course }).then((response) => {
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
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
