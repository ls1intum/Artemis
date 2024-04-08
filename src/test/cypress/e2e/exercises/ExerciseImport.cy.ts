import { Interception } from 'cypress/types/net-stubbing';
import dayjs from 'dayjs/esm';

import { Course } from 'app/entities/course.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';

import javaPartiallySuccessfulSubmission from '../../fixtures/exercise/programming/java/partially_successful/submission.json';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import {
    courseManagementAPIRequest,
    courseManagementExercises,
    courseOverview,
    exerciseAPIRequest,
    modelingExerciseCreation,
    modelingExerciseEditor,
    programmingExerciseCreation,
    programmingExerciseEditor,
    quizExerciseCreation,
    quizExerciseMultipleChoice,
    textExerciseCreation,
    textExerciseEditor,
} from '../../support/artemis';
import { admin, instructor, studentOne } from '../../support/users';
import { checkField, convertModelAfterMultiPart, generateUUID } from '../../support/utils';

describe('Import exercises', { scrollBehavior: 'center' }, () => {
    let course: Course;
    let secondCourse: Course;
    let textExercise: TextExercise;
    let quizExercise: QuizExercise;
    let modelingExercise: ModelingExercise;
    let programmingExercise: ProgrammingExercise;

    before('Setup course with exercises', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse({ customizeGroups: true }).then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addInstructorToCourse(course, instructor);
            exerciseAPIRequest.createTextExercise({ course }).then((response) => {
                textExercise = response.body;
            });
            exerciseAPIRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate]).then((response) => {
                quizExercise = convertModelAfterMultiPart(response);
            });
            exerciseAPIRequest.createModelingExercise({ course }).then((response) => {
                modelingExercise = response.body;
            });
            exerciseAPIRequest.createProgrammingExercise({ course }).then((response) => {
                programmingExercise = response.body;
            });
            courseManagementAPIRequest.createCourse({ customizeGroups: true }).then((response) => {
                secondCourse = convertModelAfterMultiPart(response);
                courseManagementAPIRequest.addStudentToCourse(secondCourse, studentOne);
                courseManagementAPIRequest.addInstructorToCourse(secondCourse, instructor);
            });
        });
    });

    after('Delete Courses', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
        courseManagementAPIRequest.deleteCourse(secondCourse, admin);
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
            courseOverview.openExerciseOverview(exercise.title!);
            courseOverview.startExercise(exercise.id!);
            courseOverview.openRunningExercise(exercise.id!);
            cy.fixture('loremIpsum-short.txt').then((submission) => {
                textExerciseEditor.shouldShowNumberOfWords(0);
                textExerciseEditor.shouldShowNumberOfCharacters(0);
                textExerciseEditor.typeSubmission(exercise.id!, submission);
                textExerciseEditor.shouldShowNumberOfWords(16);
                textExerciseEditor.shouldShowNumberOfCharacters(83);
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
            courseOverview.openExerciseOverview(exercise.title!);
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
            courseOverview.openExerciseOverview(exercise.title!);
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
        programmingExerciseCreation.setDueDate(dayjs().add(3, 'days'));

        programmingExerciseCreation.import().then((request: Interception) => {
            const exercise = request.response!.body;
            cy.login(studentOne, `/courses/${secondCourse.id}`);
            courseOverview.openExerciseOverview(exercise.title!);
            courseOverview.startExercise(exercise.id!);
            courseOverview.openRunningExercise(exercise.id!);
            programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, javaPartiallySuccessfulSubmission, () => {
                programmingExerciseEditor.getResultScore().contains(javaPartiallySuccessfulSubmission.expectedResult).and('be.visible');
            });
        });
    });
});
