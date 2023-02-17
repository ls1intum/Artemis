import { Exam } from 'app/entities/exam.model';
import { CypressExamBuilder, convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import dayjs from 'dayjs/esm';
import submission from '../../fixtures/exercise/programming/all_successful/submission.json';
import { Course } from 'app/entities/course.model';
import { generateUUID } from '../../support/utils';
import { EXERCISE_TYPE } from '../../support/constants';
import { courseManagementRequest, examExerciseGroupCreation, examNavigation, examParticipation } from '../../support/artemis';
import { AdditionalData, Exercise } from 'src/test/cypress/support/pageobjects/exam/ExamParticipation';
import { admin, studentOne } from '../../support/users';

// Common primitives
const textFixture = 'loremIpsum.txt';
const examTitle = 'exam' + generateUUID();

const exerciseArray: Array<Exercise> = [];

describe('Exam participation', () => {
    let course: Course;
    let exam: Exam;

    before(() => {
        cy.login(admin);
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
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
                courseManagementRequest.generateMissingIndividualExams(exam);
                courseManagementRequest.prepareExerciseStartForExam(exam);
            });
        });
    });

    it('Participates as a student in a registered exam', () => {
        examParticipation.startParticipation(studentOne, course, exam);
        for (let j = 0; j < exerciseArray.length; j++) {
            const exercise = exerciseArray[j];
            examNavigation.openExerciseAtIndex(j);
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
        addExerciseToArray(exerciseArray, exerciseType, response, additionalData);
    });
}

function addExerciseToArray(exerciseArray: Array<Exercise>, type: EXERCISE_TYPE, response: any, additionalData?: AdditionalData) {
    exerciseArray.push({ title: response.body.title, type, id: response.body.id, additionalData });
}
