import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Course } from 'app/entities/course.model';
import { artemis } from '../../../support/ArtemisTesting';
import { parseCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';

// pageobjects
const courseManagement = artemis.pageobjects.course.management;
const modelingEditor = artemis.pageobjects.exercise.modeling.editor;
const courseOverview = artemis.pageobjects.course.overview;
// requests
const courseManagementRequests = artemis.requests.courseManagement;
// Users
const userManagement = artemis.users;
const admin = userManagement.getAdmin();
const student = userManagement.getStudentOne();
let course: Course;
let modelingExercise: ModelingExercise;

describe('Modeling Exercise Participation Spec', () => {
    before('Log in as admin and create a course', () => {
        cy.login(admin);
        courseManagementRequests.createCourse().then((response: Cypress.Response<Course>) => {
            course = parseCourseAfterMultiPart(response);
            cy.visit(`/course-management/${course.id}`);
            courseManagement.addStudentToCourse(student);
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
        cy.login(student, `/courses/${course.id}`);
        courseOverview.startExercise(modelingExercise.id!);
        cy.get('#open-exercise-' + modelingExercise.id).click();
        modelingEditor.addComponentToModel(1);
        modelingEditor.addComponentToModel(2);
        modelingEditor.addComponentToModel(3);
        modelingEditor.submit();
    });
});
