import { artemis } from '../../../support/ArtemisTesting';
import { CypressExerciseType } from '../../../support/requests/CourseManagementRequests';

// The user management object
const users = artemis.users;

// Requests
const courseManagement = artemis.requests.courseManagement;

// PageObjects
const textEditor = artemis.pageobjects.textExercise.editor;
const courseOverview = artemis.pageobjects.courseOverview;

describe('Text exercise participation', () => {
    let course: any;
    let exercise: any;

    before(() => {
        cy.login(users.getAdmin());
        courseManagement.createCourse().then((response) => {
            course = response.body;
            courseManagement.addStudentToCourse(course.id, users.getStudentOne().username);
            courseManagement.createTextExercise({ course }).then((request: any) => {
                exercise = request.body;
            });
        });
    });

    it('Creates a text exercise in the UI', () => {
        cy.login(users.getStudentOne(), `/courses/${course.id}/exercises`);
        courseOverview.startExercise(exercise.id, CypressExerciseType.TEXT);
        courseOverview.openRunningExercise(exercise.id);

        // Verify the initial state of the text editor
        textEditor.shouldShowExerciseTitleInHeader(exercise.title);
        textEditor.shouldShowProblemStatement();

        // Make a submission
        cy.fixture('loremIpsum.txt').then((submission) => {
            textEditor.shouldShowNumberOfWords(0);
            textEditor.shouldShowNumberOfCharacters(0);
            textEditor.typeSubmission(submission);
            textEditor.shouldShowNumberOfWords(100);
            textEditor.shouldShowNumberOfCharacters(591);
            textEditor
                .submit()
                .its('response')
                .then((response: any) => {
                    expect(response.body.text).equals(submission);
                    expect(response.body.submitted).equals(true);
                    expect(response.statusCode).equals(200);
                });
        });
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagement.deleteCourse(course.id);
        }
    });
});
