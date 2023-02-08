import { Exam } from 'app/entities/exam.model';
import { CypressExamBuilder, convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import dayjs from 'dayjs/esm';
import submission from '../../fixtures/programming_exercise_submissions/all_successful/submission.json';
import { Course } from 'app/entities/course.model';
import { generateUUID } from '../../support/utils';
import { EXERCISE_TYPE } from '../../support/constants';
import { courseManagementRequest, examExerciseGroupCreation, examParticipation, examStartEnd } from '../../support/artemis';
import { AdditionalData, Exercise } from 'src/test/cypress/support/pageobjects/exam/ExamParticipation';
import { Interception } from 'cypress/types/net-stubbing';
import { admin, studentOne, studentTwo } from '../../support/users';

// Common primitives
const textFixture = 'loremIpsum.txt';
let exerciseArray: Array<Exercise> = [];

describe('Exam participation', () => {
    let course: Course;

    describe('Early Hand-in', () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        before(() => {
            cy.login(admin);
            courseManagementRequest.createCourse(true).then((response) => {
                course = convertCourseAfterMultiPart(response);
                const examContent = new CypressExamBuilder(course)
                    .title(examTitle)
                    .visibleDate(dayjs().subtract(3, 'minutes'))
                    .startDate(dayjs().subtract(2, 'minutes'))
                    .endDate(dayjs().add(1, 'hour'))
                    .examMaxPoints(40)
                    .numberOfExercises(4)
                    .build();
                courseManagementRequest.createExam(examContent).then((examResponse) => {
                    exam = examResponse.body;
                    addGroupWithExercise(exam, EXERCISE_TYPE.Text, { textFixture });

                    addGroupWithExercise(exam, EXERCISE_TYPE.Programming, { submission });

                    addGroupWithExercise(exam, EXERCISE_TYPE.Quiz, { quizExerciseID: 0 });

                    addGroupWithExercise(exam, EXERCISE_TYPE.Modeling);

                    courseManagementRequest.registerStudentForExam(exam, studentOne);
                    courseManagementRequest.registerStudentForExam(exam, studentTwo);
                    courseManagementRequest.generateMissingIndividualExams(exam);
                    courseManagementRequest.prepareExerciseStartForExam(exam);
                });
            });
        });

        it('Participates as a student in a registered exam', () => {
            examParticipation.startParticipation(studentOne, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                examParticipation.openExercise(j);
                examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
            }
            examParticipation.handInEarly();
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                examParticipation.verifyExerciseTitleOnFinalPage(exercise.id, exercise.title);
                if (exercise.type === EXERCISE_TYPE.Text) {
                    examParticipation.verifyTextExerciseOnFinalPage(exercise.additionalData!.textFixture!);
                }
            }
            examParticipation.checkExamTitle(examTitle);
        });

        it('Using save and continue to navigate within exam', () => {
            examParticipation.startParticipation(studentTwo, course, exam);
            examParticipation.openExercise(0);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type == EXERCISE_TYPE.Programming) {
                    examParticipation.openExercise(j + 1);
                } else {
                    examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
                    examParticipation.clickSaveAndContinue();
                }
            }
            examParticipation.handInEarly();
        });
    });

    describe('Normal Hand-in', () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        before(() => {
            exerciseArray = [];

            cy.login(admin);
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'minutes'))
                .startDate(dayjs().subtract(2, 'minutes'))
                .endDate(dayjs().add(20, 'seconds'))
                .examMaxPoints(10)
                .numberOfExercises(1)
                .build();
            courseManagementRequest.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                addGroupWithExercise(exam, EXERCISE_TYPE.Text, { textFixture });

                courseManagementRequest.registerStudentForExam(exam, studentOne);
                courseManagementRequest.generateMissingIndividualExams(exam);
                courseManagementRequest.prepareExerciseStartForExam(exam);
            });
        });

        it('Participates as a student in a registered exam', () => {
            examParticipation.startParticipation(studentOne, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            examParticipation.openExercise(textExerciseIndex);
            examParticipation.makeSubmission(textExercise.id, textExercise.type, textExercise.additionalData);
            examParticipation.clickSaveAndContinue();
            cy.get('#fullname', { timeout: 20000 }).should('be.visible');
            examStartEnd.finishExam().then((request: Interception) => {
                expect(request.response!.statusCode).to.eq(200);
            });
            examParticipation.verifyTextExerciseOnFinalPage(textExercise.additionalData!.textFixture!);
            examParticipation.checkExamTitle(examTitle);
        });
    });

    after(() => {
        if (course) {
            cy.login(admin);
            courseManagementRequest.deleteCourse(course.id!);
        }
    });
});

function addGroupWithExercise(exam: Exam, exerciseType: EXERCISE_TYPE, additionalData?: AdditionalData) {
    examExerciseGroupCreation.addGroupWithExercise(exam, 'Exercise ' + generateUUID(), exerciseType, (response) => {
        if (exerciseType == EXERCISE_TYPE.Quiz) {
            additionalData!.quizExerciseID = response.body.quizQuestions![0].id;
        }
        addExerciseToArray(exerciseType, response, additionalData);
    });
}

function addExerciseToArray(type: EXERCISE_TYPE, response: any, additionalData?: AdditionalData) {
    exerciseArray.push({ title: response.body.title, type, id: response.body.id, additionalData });
}
