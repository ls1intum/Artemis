import { Interception } from 'cypress/types/net-stubbing';
import { TextExercise } from 'app/entities/text-exercise.model';
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
const textEditor = artemis.pageobjects.exercise.text.editor;
const courseOverview = artemis.pageobjects.course.overview;

describe('Text exercise participation', () => {
    let course: Course;
    let exercise: TextExercise;

    before(() => {
        cy.login(admin);
        courseManagementRequests.createCourse().then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequests.addStudentToCourse(course, studentOne);
            courseManagementRequests.createTextExercise({ course }).then((exerciseResponse: Cypress.Response<TextExercise>) => {
                exercise = exerciseResponse.body;
            });
        });
    });

    it('Creates a text exercise in the UI', () => {
        cy.login(studentOne, `/courses/${course.id}/exercises`);
        courseOverview.startExercise(exercise.id!);
        courseOverview.openRunningExercise(exercise.id!);

        // Verify the initial state of the text editor
        textEditor.shouldShowExerciseTitleInHeader(exercise.title!);
        textEditor.shouldShowProblemStatement();

        // Make a submission
        cy.fixture('loremIpsum.txt').then((submission) => {
            textEditor.shouldShowNumberOfWords(0);
            textEditor.shouldShowNumberOfCharacters(0);
            textEditor.typeSubmission(exercise.id!, submission);
            textEditor.shouldShowNumberOfWords(100);
            textEditor.shouldShowNumberOfCharacters(591);
            textEditor.submit().then((request: Interception) => {
                expect(request.response!.body.text).to.eq(submission);
                expect(request.response!.body.submitted).to.eq(true);
                expect(request.response!.statusCode).to.eq(200);
            });
        });
    });

    after(() => {
        if (course) {
            cy.login(admin);
            courseManagementRequests.deleteCourse(course.id!);
        }
    });
});
