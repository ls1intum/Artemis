import { Interception } from 'cypress/types/net-stubbing';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Course } from 'app/entities/course.model';
import { artemis } from '../../../support/ArtemisTesting';

// The user management object
const users = artemis.users;

// Requests
const courseManagement = artemis.requests.courseManagement;

// PageObjects
const textEditor = artemis.pageobjects.exercise.text.editor;
const courseOverview = artemis.pageobjects.course.overview;

describe('Text exercise participation', () => {
    let course: Course;
    let exercise: TextExercise;

    before(() => {
        cy.login(users.getAdmin());
        courseManagement.createCourse().then((response) => {
            course = response.body;
            courseManagement.addStudentToCourse(course, users.getStudentOne());
            courseManagement.createTextExercise({ course }).then((exerciseResponse: Cypress.Response<TextExercise>) => {
                exercise = exerciseResponse.body;
            });
        });
    });

    it('Creates a text exercise in the UI', () => {
        cy.login(users.getStudentOne(), `/courses/${course.id}/exercises`);
        courseOverview.startExercise(exercise.id!);
        courseOverview.openRunningExercise(exercise.id!);

        // Verify the initial state of the text editor
        textEditor.shouldShowExerciseTitleInHeader(exercise.title!);
        textEditor.shouldShowProblemStatement();

        // Make a submission
        cy.fixture('loremIpsum.txt').then((submission) => {
            textEditor.shouldShowNumberOfWords(0);
            textEditor.shouldShowNumberOfCharacters(0);
            textEditor.typeSubmission(submission);
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
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagement.deleteCourse(course.id!);
        }
    });
});
