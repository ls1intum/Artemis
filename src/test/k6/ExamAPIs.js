import { login } from './requests/requests.js';
import { group, sleep } from 'k6';
import { getQuizQuestions, simulateQuizWork } from './requests/quiz.js';
import { newCourse, deleteCourse } from './requests/course.js';
import { createUsersIfNeeded } from './requests/user.js';
import { createQuizExercise, deleteQuizExercise, submitRandomAnswerRESTExam } from './requests/quiz.js';
import { newExam, newExerciseGroup, newTextExercise, addUserToStudentsInExam, generateExams, startExercises, getExamForUser } from './requests/exam.js';
import { submitRandomTextAnswerExam } from './requests/text.js';

// Version: 1.1
// Creator: Firefox
// Browser: Firefox

export let options = {
    maxRedirects: 0,
    iterations: __ENV.ITERATIONS,
    vus: __ENV.ITERATIONS,
    rps: 5,
    setupTimeout: '480s',
    teardownTimeout: '240s',
};

const adminUsername = __ENV.ADMIN_USERNAME;
const adminPassword = __ENV.ADMIN_PASSWORD;
let baseUsername = __ENV.BASE_USERNAME;
let basePassword = __ENV.BASE_PASSWORD;
let userOffset = parseInt(__ENV.USER_OFFSET);

export function setup() {
    console.log('__ENV.CREATE_USERS: ' + __ENV.CREATE_USERS);
    console.log('__ENV.TIMEOUT_PARTICIPATION: ' + __ENV.TIMEOUT_PARTICIPATION);
    console.log('__ENV.TIMEOUT_EXERCISE: ' + __ENV.TIMEOUT_EXERCISE);
    console.log('__ENV.ITERATIONS: ' + __ENV.ITERATIONS);
    console.log('__ENV.USER_OFFSET: ' + __ENV.USER_OFFSET);

    let artemis, exerciseId, course, userId, textExercise, quizExercise;

    const iterations = parseInt(__ENV.ITERATIONS);

    if (parseInt(__ENV.COURSE_ID) === 0 || parseInt(__ENV.EXERCISE_ID) === 0) {
        console.log('Creating new course and exercise as no parameters are given');

        // Create course
        artemis = login(adminUsername, adminPassword);

        course = newCourse(artemis);

        createUsersIfNeeded(artemis, baseUsername, basePassword, adminUsername, adminPassword, course, userOffset);

        const instructorUsername = baseUsername.replace('USERID', '1');
        const instructorPassword = basePassword.replace('USERID', '1');

        // Login to Artemis
        artemis = login(instructorUsername, instructorPassword);

        // it might be necessary that the newly created groups or accounts are synced with the version control and continuous integration servers, so we wait for 1 minute
        const timeoutExercise = parseFloat(__ENV.TIMEOUT_EXERCISE);
        if (timeoutExercise > 0) {
            console.log('Wait ' + timeoutExercise + 's before creating the quiz exercise so that the setup can finish properly');
            sleep(timeoutExercise);
        }

        // Create new exam
        let exam = newExam(artemis, course);

        let exerciseGroup1 = newExerciseGroup(artemis, exam);
        let exerciseGroup2 = newExerciseGroup(artemis, exam);

        let textExercise = newTextExercise(artemis, exerciseGroup1);

        let quizExercise = createQuizExercise(artemis, null, exerciseGroup2, false);

        for (let i = 1; i <= iterations; i++) {
            addUserToStudentsInExam(artemis, baseUsername.replace('USERID', i + userOffset), exam);
        }

        generateExams(artemis, exam);

        startExercises(artemis, exam);

        sleep(2);

        return { courseId: exam.course.id, examId: exam.id };
    } else {
        console.log('Using existing course and exercise');
        return { exerciseId: parseInt(__ENV.EXERCISE_ID), courseId: parseInt(__ENV.COURSE_ID) };
    }
}

export default function (data) {
    const websocketConnectionTime = parseFloat(__ENV.TIMEOUT_PARTICIPATION); // Time in seconds the websocket is kept open, if set to 0 no websocket connection is estahblished

    // Delay so that not all users start at the same time, batches of 50 users per second
    const delay = Math.floor(__VU / 50);
    sleep(delay);

    group('Artemis Exam Stresstest', function () {
        const userId = parseInt(__VU) + userOffset;
        const currentUsername = baseUsername.replace('USERID', userId);
        const currentPassword = basePassword.replace('USERID', userId);
        const artemis = login(currentUsername, currentPassword);

        sleep(30);

        const studentExam = getExamForUser(artemis, data.courseId, data.examId);

        console.log(studentExam.exam.startDate);
        const parsedStartDate = new Date(Date.parse(studentExam.exam.startDate));

        console.log(parsedStartDate);

        const currentTime = Date.now();
        const differenceInMilliSeconds = parsedStartDate - currentTime;
        const timeUntilExamStart = differenceInMilliSeconds / 1000;

        console.log(`Waiting ${timeUntilExamStart}s for exam start`);
        sleep(1 + timeUntilExamStart);

        for (const exercise of studentExam.exercises) {
            console.log(`Exercise is of type ${exercise.type}`);
            switch (exercise.type) {
                case 'quiz':
                    const studentParticipations = exercise.studentParticipations;
                    const submissions = studentParticipations[0].submissions;
                    const submissionId = submissions[0].id;
                    for (let i = 1; i <= exercise.quizQuestions.length; i++) {
                        submitRandomAnswerRESTExam(artemis, exercise, i, submissionId);
                        sleep(1);
                    }
                    break;

                case 'text':
                    for (let i = 0; i <= 10; i++) {
                        submitRandomTextAnswerExam(artemis, exercise, submissionId);
                        sleep(1);
                    }
                    break;
            }
        }

        // console.log('Received EXAM ' + JSON.stringify(studentExam) + ' for user ' + baseUsername.replace('USERID', userId));
    });

    return data;
}

export function teardown(data) {
    const shouldCleanup = __ENV.CLEANUP === true || __ENV.CLEANUP === 'true';
    if (shouldCleanup) {
        const artemis = login(adminUsername, adminPassword);
        const courseId = data.courseId;
        const exerciseId = data.exerciseId;

        deleteQuizExercise(artemis, exerciseId);
        deleteCourse(artemis, courseId);
    }
}
