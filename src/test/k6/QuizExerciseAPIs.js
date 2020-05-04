import { login } from './requests/requests.js';
import { group, sleep } from 'k6';
import { getQuizQuestions, simulateQuizWork } from './requests/quiz.js';
import { newCourse, deleteCourse } from './requests/course.js';
import { createUsersIfNeeded } from './requests/user.js';
import { createQuizExercise, deleteQuizExercise } from './requests/quiz.js';

// Version: 1.1
// Creator: Firefox
// Browser: Firefox

export let options = {
    maxRedirects: 0,
    iterations: __ENV.ITERATIONS,
    vus: __ENV.ITERATIONS,
    rps: 5,
    setupTimeout: '240s',
    teardownTimeout: '240s',
};

const adminUsername = __ENV.ADMIN_USERNAME;
const adminPassword = __ENV.ADMIN_PASSWORD;
let baseUsername = __ENV.BASE_USERNAME;
let basePassword = __ENV.BASE_PASSWORD;

export function setup() {
    console.log('__ENV.CREATE_USERS: ' + __ENV.CREATE_USERS);
    console.log('__ENV.TIMEOUT_PARTICIPATION: ' + __ENV.TIMEOUT_PARTICIPATION);
    console.log('__ENV.TIMEOUT_EXERCISE: ' + __ENV.TIMEOUT_EXERCISE);
    console.log('__ENV.ITERATIONS: ' + __ENV.ITERATIONS);

    let artemis, exerciseId, course, userId;

    // Create course
    artemis = login(adminUsername, adminPassword);

    course = newCourse(artemis);

    createUsersIfNeeded(artemis, baseUsername, basePassword, adminUsername, adminPassword, course);

    const instructorUsername = baseUsername.replace('USERID', '1');
    const instructorPassword = basePassword.replace('USERID', '1');

    // Login to Artemis
    artemis = login(instructorUsername, instructorPassword);

    // it might be necessary that the newly created groups or accounts are synced with the version control and continuous integration servers, so we wait for 1 minute
    const timeoutExercise = parseFloat(__ENV.TIMEOUT_EXERCISE);
    if (timeoutExercise > 0) {
        console.log('Wait ' + timeoutExercise + 's before creating the programming exercise so that the setup can finish properly');
        sleep(timeoutExercise);
    }

    // Create new exercise
    exerciseId = createQuizExercise(artemis, course);

    sleep(2);

    return { exerciseId: exerciseId, courseId: course.id };
}

export default function (data) {
    const websocketConnectionTime = parseFloat(__ENV.TIMEOUT_PARTICIPATION); // Time in seconds the websocket is kept open, if set to 0 no websocket connection is estahblished

    // Delay so that not all users start at the same time, batches of 50 users per second
    const delay = Math.floor(__VU / 50);
    sleep(delay);

    group('Artemis Programming Exercise Participation Websocket Stresstest', function () {
        const userId = __VU;
        const currentUsername = baseUsername.replace('USERID', userId);
        const currentPassword = basePassword.replace('USERID', userId);
        const artemis = login(currentUsername, currentPassword);

        const questions = getQuizQuestions(artemis, data.courseId, data.exerciseId);
        const remainingTime = websocketConnectionTime - delay;
        const startTime = new Date().getTime();
        while ((new Date().getTime() - startTime) / 1000 < remainingTime) {
            simulateQuizWork(artemis, data.exerciseId, questions, 30);
        }
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
