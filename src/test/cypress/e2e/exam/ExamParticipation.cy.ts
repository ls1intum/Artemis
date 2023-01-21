import { Exam } from 'app/entities/exam.model';
import { CypressExamBuilder, convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { artemis } from '../../support/ArtemisTesting';
import dayjs from 'dayjs/esm';
import submission from '../../fixtures/programming_exercise_submissions/all_successful/submission.json';
import { Course } from 'app/entities/course.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { generateUUID } from '../../support/utils';
import { AdditionalData, Exercise } from 'src/test/cypress/support/pageobjects/exam/ExamParticipation';

// User management
const users = artemis.users;
const student = users.getStudentOne();

// Requests
const courseRequests = artemis.requests.courseManagement;

// PageObjects
const examParticipation = artemis.pageobjects.exam.participation;
const exerciseGroupCreation = artemis.pageobjects.exam.exerciseGroupCreation;

// Common primitives
const textFixture = 'loremIpsum.txt';
const examTitle = 'exam' + generateUUID();

const exerciseArray: Array<Exercise> = [];

describe('Exam participation', () => {
    let course: Course;
    let exam: Exam;

    before(() => {
        cy.login(users.getAdmin());
        courseRequests.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .examMaxPoints(40)
                .numberOfExercises(4)
                .build();
            courseRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });

                addGroupWithExercise(exam, ExerciseType.PROGRAMMING, { submission });

                addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 });

                addGroupWithExercise(exam, ExerciseType.MODELING);

                courseRequests.registerStudentForExam(exam, student);
                courseRequests.generateMissingIndividualExams(exam);
                courseRequests.prepareExerciseStartForExam(exam);
            });
        });
    });

    it('Participates as a student in a registered exam', () => {
        examParticipation.startParticipation(student, course, exam);
        for (let j = 0; j < exerciseArray.length; j++) {
            const exercise = exerciseArray[j];
            examParticipation.openExercise(j);
            examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
        }
        examParticipation.handInEarly();
        for (let j = 0; j < exerciseArray.length; j++) {
            const exercise = exerciseArray[j];
            examParticipation.verifyExerciseTitleOnFinalPage(exercise.id, exercise.title);
            if (exercise.type === ExerciseType.TEXT) {
                examParticipation.verifyTextExerciseOnFinalPage(exercise.additionalData!.textFixture!);
            }
        }
        examParticipation.checkExamTitle(examTitle);
    });

    after(() => {
        if (course) {
            cy.login(users.getAdmin());
            courseRequests.deleteCourse(course.id!);
        }
    });
});

function addGroupWithExercise(exam: Exam, exerciseType: ExerciseType, additionalData?: AdditionalData) {
    exerciseGroupCreation.addGroupWithExercise(exam, 'Exercise ' + generateUUID(), exerciseType, (response) => {
        if (exerciseType == ExerciseType.QUIZ) {
            additionalData!.quizExerciseID = response.body.quizQuestions![0].id;
        }
        addExerciseToArray(exerciseArray, exerciseType, response, additionalData);
    });
}

function addExerciseToArray(exerciseArray: Array<Exercise>, type: ExerciseType, response: any, additionalData?: AdditionalData) {
    exerciseArray.push({ title: response.body.title, type, id: response.body.id, additionalData });
}
