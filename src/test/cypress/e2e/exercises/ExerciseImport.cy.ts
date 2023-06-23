import dayjs from 'dayjs/esm';

import { TextExercise } from 'app/entities/text-exercise.model';
import {
    courseManagementExercises,
    courseManagementRequest,
    courseOverview,
    modelingExerciseCreation,
    modelingExerciseEditor,
    programmingExerciseCreation,
    programmingExerciseEditor,
    quizExerciseCreation,
    quizExerciseMultipleChoice,
    textExerciseCreation,
    textExerciseEditor,
} from '../../support/artemis';
import { convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { admin, instructor, studentOne } from '../../support/users';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import partiallySuccessful from '../../fixtures/exercise/programming/partially_successful/submission.json';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { Interception } from 'cypress/types/net-stubbing';
import { checkField, generateUUID } from '../../support/utils';

describe('Import exercises', () => {
    let course: Course;
    let secondCourse: Course;
    let textExercise: TextExercise;
    let quizExercise: QuizExercise;
    let modelingExercise: ModelingExercise;
    let programmingExercise: ProgrammingExercise;

    before('Setup course with exercises', () => {
        cy.login(admin);
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequest.addInstructorToCourse(course, instructor);
            courseManagementRequest.createTextExercise({ course }).then((response) => {
                textExercise = response.body;
            });
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate]).then((response) => {
                quizExercise = response.body;
            });
            courseManagementRequest.createModelingExercise({ course }).then((response) => {
                modelingExercise = response.body;
            });
            courseManagementRequest.createProgrammingExercise({ course }).then((response) => {
                programmingExercise = response.body;
            });
            courseManagementRequest.createCourse(true).then((response) => {
                secondCourse = convertCourseAfterMultiPart(response);
                courseManagementRequest.addStudentToCourse(secondCourse, studentOne);
                courseManagementRequest.addInstructorToCourse(secondCourse, instructor);
            });
        });
    });

    it('Imports text exercise', () => {
        cy.login(instructor, `/course-management/${secondCourse.id}/exercises`);
        courseManagementExercises.importTextExercise();
        courseManagementExercises.clickImportExercise(textExercise.id!);

        checkField('#field_title', textExercise.title!);
        checkField('#field_points', textExercise.maxPoints!);

        textExerciseCreation.setReleaseDate(dayjs());
        textExerciseCreation.setDueDate(dayjs().add(1, 'days'));
        textExerciseCreation.setAssessmentDueDate(dayjs().add(2, 'days'));

        textExerciseCreation.import().then((request: Interception) => {
            const exercise = request.response!.body;
            cy.login(studentOne, `/courses/${secondCourse.id}`);
            courseOverview.startExercise(exercise.id!);
            courseOverview.openRunningExercise(exercise.id!);
            cy.fixture('loremIpsum.txt').then((submission) => {
                textExerciseEditor.shouldShowNumberOfWords(0);
                textExerciseEditor.shouldShowNumberOfCharacters(0);
                textExerciseEditor.typeSubmission(exercise.id!, submission);
                textExerciseEditor.shouldShowNumberOfWords(100);
                textExerciseEditor.shouldShowNumberOfCharacters(591);
                textExerciseEditor.submit().then((request: Interception) => {
                    expect(request.response!.body.text).to.eq(submission);
                    expect(request.response!.body.submitted).to.be.true;
                    expect(request.response!.statusCode).to.eq(200);
                });
            });
        });
    });

    it('Imports quiz exercise', () => {
        cy.login(instructor, `/course-management/${secondCourse.id}/exercises`);
        courseManagementExercises.importQuizExercise();
        courseManagementExercises.clickImportExercise(quizExercise.id!);

        checkField('#field_title', quizExercise.title!);
        checkField('#quiz-duration-minutes', quizExercise.duration! / 60);

        cy.wait(500);

        quizExerciseCreation.setVisibleFrom(dayjs());

        quizExerciseCreation.import().then((request: Interception) => {
            const exercise = request.response!.body;
            courseManagementExercises.startQuiz(exercise.id!);
            cy.login(studentOne, `/courses/${secondCourse.id}`);
            courseOverview.startExercise(exercise.id!);
            quizExerciseMultipleChoice.tickAnswerOption(exercise.id!, 0);
            quizExerciseMultipleChoice.tickAnswerOption(exercise.id!, 2);
            quizExerciseMultipleChoice.submit().then((request: Interception) => {
                expect(request.response!.body.submitted).to.be.true;
                expect(request.response!.statusCode).to.eq(200);
            });
        });
    });

    it('Imports modeling exercise', () => {
        cy.login(instructor, `/course-management/${secondCourse.id}/exercises`);
        courseManagementExercises.importModelingExercise();
        courseManagementExercises.clickImportExercise(modelingExercise.id!);

        checkField('#field_title', modelingExercise.title!);
        checkField('#field_points', modelingExercise.maxPoints!);

        modelingExerciseCreation.setReleaseDate(dayjs());
        modelingExerciseCreation.setDueDate(dayjs().add(1, 'days'));
        modelingExerciseCreation.setAssessmentDueDate(dayjs().add(2, 'days'));

        modelingExerciseCreation.import().then((request: Interception) => {
            const exercise = request.response!.body;
            cy.login(studentOne, `/courses/${secondCourse.id}`);
            courseOverview.startExercise(exercise.id!);
            courseOverview.openRunningExercise(exercise.id!);
            modelingExerciseEditor.addComponentToModel(exercise.id!, 1);
            modelingExerciseEditor.addComponentToModel(exercise.id!, 2);
            modelingExerciseEditor.addComponentToModel(exercise.id!, 3);
            modelingExerciseEditor.submit().then((request: Interception) => {
                expect(request.response!.body.submitted).to.be.true;
                expect(request.response!.statusCode).to.eq(200);
            });
        });
    });

    it('Imports programming exercise', () => {
        cy.login(instructor, `/course-management/${secondCourse.id}/exercises`);
        courseManagementExercises.importProgrammingExercise();
        courseManagementExercises.clickImportExercise(programmingExercise.id!);

        checkField('#field_points', programmingExercise.maxPoints!);

        programmingExerciseCreation.setTitle('Import Test');
        programmingExerciseCreation.setShortName('importtest' + generateUUID());

        programmingExerciseCreation.import().then((request: Interception) => {
            const exercise = request.response!.body;
            cy.login(studentOne, `/courses/${secondCourse.id}`);
            courseOverview.startExercise(exercise.id!);
            courseOverview.openRunningExercise(exercise.id!);
            programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, exercise.packageName!, partiallySuccessful, () => {
                programmingExerciseEditor.getResultScore().contains(partiallySuccessful.expectedResult).and('be.visible');
            });
        });
    });

    after('Delete Courses', () => {
        cy.login(admin);
        if (course.id) {
            courseManagementRequest.deleteCourse(course.id).its('status').should('eq', 200);
        }
        if (secondCourse.id) {
            courseManagementRequest.deleteCourse(secondCourse.id).its('status').should('eq', 200);
        }
    });
});
