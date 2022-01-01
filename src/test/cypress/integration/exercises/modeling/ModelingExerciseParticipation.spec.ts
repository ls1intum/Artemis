import { artemis } from '../../../support/ArtemisTesting';

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
let course: any;
let modelingExercise: any;

describe('Modeling Exercise Participation Spec', () => {
    before('Log in as admin and create a course', () => {
        cy.login(admin);
        courseManagementRequests.createCourse().then((courseResp: any) => {
            course = courseResp.body;
            cy.visit(`/course-management/${course.id}`);
            courseManagement.addStudentToCourse(student);
            courseManagementRequests.createModelingExercise({ course }).then((resp: any) => {
                modelingExercise = resp.body;
            });
        });
    });

    after('Delete the test course', () => {
        cy.login(admin);
        courseManagementRequests.deleteCourse(course.id);
    });

    it('Student can start and submit their model', () => {
        cy.login(student, `/courses/${course.id}`);
        courseOverview.startExercise(modelingExercise.id);
        cy.get('#open-exercise-' + modelingExercise.id).click();
        modelingEditor.addComponentToModel(1);
        modelingEditor.addComponentToModel(2);
        modelingEditor.addComponentToModel(3);
        modelingEditor.submit();
    });
});
