import { Interception } from 'cypress/types/net-stubbing';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { DELETE } from '../../../support/constants';
import { artemis } from '../../../support/ArtemisTesting';
import { generateUUID } from '../../../support/utils';
import { PROGRAMMING_EXERCISE_BASE } from '../../../support/requests/CourseManagementRequests';

//  Admin account
const admin = artemis.users.getAdmin();

// Requests
const artemisRequests = artemis.requests;

// PageObjects
const courseManagementPage = artemis.pageobjects.course.management;
const navigationBar = artemis.pageobjects.navigationBar;
const programmingCreation = artemis.pageobjects.exercise.programming.creation;
const courseExercises = artemis.pageobjects.course.managementExercises;

describe('Programming Exercise Management', () => {
    let course: Course;

    before(() => {
        cy.login(admin);
        artemisRequests.courseManagement
            .createCourse(true)
            .its('body')
            .then((body) => {
                expect(body).property('id').to.be.a('number');
                course = body;
            });
    });

    describe('Programming exercise deletion', () => {
        let programmingExercise: ProgrammingExercise;

        beforeEach(() => {
            artemisRequests.courseManagement
                .createProgrammingExercise({ course })
                .its('body')
                .then((exercise) => {
                    expect(exercise).to.not.be.null;
                    programmingExercise = exercise;
                });
        });

        it('Deletes an existing programming exercise', function () {
            cy.login(admin, '/').wait(500);
            navigationBar.openCourseManagement();
            courseManagementPage.openExercisesOfCourse(course.shortName!);
            cy.get('#delete-exercise').click();
            // Check all checkboxes to get rid of the git repositories and build plans
            cy.get('#additional-check-0').check();
            cy.get('#additional-check-1').check();
            cy.get('#confirm-exercise-name').type(programmingExercise.title!);
            cy.intercept(DELETE, PROGRAMMING_EXERCISE_BASE + '*').as('deleteProgrammingExerciseQuery');
            // For some reason the deletion sometimes fails if we do it immediately
            cy.get('#delete').click();
            cy.wait('@deleteProgrammingExerciseQuery').then((request: any) => {
                expect(request.response.statusCode).to.equal(200);
            });
            cy.contains(programmingExercise.title!).should('not.exist');
        });
    });

    describe('Programming exercise creation', () => {
        it('Creates a new programming exercise', function () {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagementPage.openExercisesOfCourse(course.shortName!);
            courseExercises.clickCreateProgrammingExerciseButton();
            cy.url().should('include', '/programming-exercises/new');
            cy.log('Filling out programming exercise info...');
            const exerciseTitle = 'Cypress programming exercise ' + generateUUID();
            programmingCreation.setTitle(exerciseTitle);
            programmingCreation.setShortName('cypress' + generateUUID());
            programmingCreation.setPackageName('de.test');
            programmingCreation.setPoints(100);
            programmingCreation.checkAllowOnlineEditor();
            programmingCreation.generate().then((request: Interception) => {
                const exercise = request.response!.body;
                cy.url().should('include', '/exercises');
                courseExercises.shouldContainExerciseWithName(exercise.id);
            });
        });
    });

    after(() => {
        if (!!course) {
            artemisRequests.courseManagement.deleteCourse(course.id!);
        }
    });
});
