import { Interception } from 'cypress/types/net-stubbing';

import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';

import { courseManagementAPIRequest, courseOverview, exerciseAPIRequest, textExerciseEditor } from '../../../support/artemis';
import { admin, studentOne } from '../../../support/users';
import { convertModelAfterMultiPart } from '../../../support/utils';

describe('Text exercise participation', () => {
    let course: Course;
    let exercise: TextExercise;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
            exerciseAPIRequest.createTextExercise({ course }).then((exerciseResponse: Cypress.Response<TextExercise>) => {
                exercise = exerciseResponse.body;
            });
        });
    });

    it('Makes a text exercise submission as student', () => {
        cy.login(studentOne, `/courses/${course.id}/exercises`);
        courseOverview.startExercise(exercise.id!);
        courseOverview.openRunningExercise(exercise.id!);

        // Verify the initial state of the text editor
        textExerciseEditor.shouldShowExerciseTitleInHeader(exercise.title!);
        textExerciseEditor.shouldShowProblemStatement();

        // Make a submission
        cy.fixture('loremIpsum.txt').then((submission) => {
            textExerciseEditor.shouldShowNumberOfWords(0);
            textExerciseEditor.shouldShowNumberOfCharacters(0);
            textExerciseEditor.typeSubmission(exercise.id!, submission);
            textExerciseEditor.shouldShowNumberOfWords(74);
            textExerciseEditor.shouldShowNumberOfCharacters(451);
            textExerciseEditor.submit().then((request: Interception) => {
                expect(request.response!.body.text).to.eq(submission);
                expect(request.response!.body.submitted).to.be.true;
                expect(request.response!.statusCode).to.eq(200);
            });
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
