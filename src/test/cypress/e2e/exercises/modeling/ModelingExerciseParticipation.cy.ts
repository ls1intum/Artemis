import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Course } from 'app/entities/course.model';
import { artemis } from '../../../support/ArtemisTesting';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';

// Users
const users = artemis.users;
const admin = users.getAdmin();
const studentOne = users.getStudentOne();

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// PageObjects
const modelingEditor = artemis.pageobjects.exercise.modeling.editor;
const courseOverview = artemis.pageobjects.course.overview;

// Common primitives
let course: Course;
let modelingExercise: ModelingExercise;

describe('Modeling Exercise Participation Spec', () => {
    before('Log in as admin and create a course', () => {
        cy.login(admin);
        courseManagementRequests.createCourse().then((response: Cypress.Response<Course>) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequests.addStudentToCourse(course, studentOne);
            courseManagementRequests.createModelingExercise({ course }).then((resp: Cypress.Response<ModelingExercise>) => {
                modelingExercise = resp.body;
            });
        });
    });

    after('Delete the test course', () => {
        cy.login(admin);
        courseManagementRequests.deleteCourse(course.id!);
    });

    it('Student can start and submit their model', () => {
        cy.login(studentOne, `/courses/${course.id}`);
        cy.reloadUntilFound('#start-exercise-' + modelingExercise.id);
        courseOverview.startExercise(modelingExercise.id!);
        cy.get('#open-exercise-' + modelingExercise.id).click();
        modelingEditor.addComponentToModel(modelingExercise.id!, 1);
        modelingEditor.addComponentToModel(modelingExercise.id!, 2);
        modelingEditor.addComponentToModel(modelingExercise.id!, 3);
        modelingEditor.submit();
    });
});
