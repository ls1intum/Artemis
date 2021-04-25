import { group, sleep } from 'k6';
import { addUserToInstructorsInCourse, addUserToTutorsInCourse, deleteCourse, getCourse, newCourse } from './requests/course.js';
import { evaluateQuizzes } from './requests/exam.js';
import { assessModelingSubmission, getAndLockModelingSubmission, newModelingExercise, submitRandomModelingAnswerExam } from './requests/modeling.js';
import { login } from './requests/requests.js';
import { createTutorsIfNeeded, createUsersIfNeeded } from './requests/user.js';

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
const onlyPrepare = __ENV.ONLY_PREPARE === true || __ENV.ONLY_PREPARE === 'true';

export function setup() {
    console.log('__ENV.CREATE_USERS: ' + __ENV.CREATE_USERS);
    console.log('__ENV.TIMEOUT_PARTICIPATION: ' + __ENV.TIMEOUT_PARTICIPATION);
    console.log('__ENV.TIMEOUT_EXERCISE: ' + __ENV.TIMEOUT_EXERCISE);
    console.log('__ENV.ITERATIONS: ' + __ENV.ITERATIONS);
    console.log('__ENV.USER_OFFSET: ' + __ENV.USER_OFFSET);
    console.log('__ENV.ONLY_PREPARE: ' + onlyPrepare);

    const artemisAdmin = login(adminUsername, adminPassword);

    let course;

    if (parseInt(__ENV.COURSE_ID) === 0 || parseInt(__ENV.EXERCISE_ID) === 0) {
        console.log('Creating new course as no course parameters are given');
        course = newCourse(artemisAdmin);
    } else {
        course = getCourse(artemisAdmin, parseInt(__ENV.COURSE_ID));
    }

    createTutorsIfNeeded(artemisAdmin, baseUsername, basePassword, adminUsername, adminPassword, course, userOffset);

    if (parseInt(__ENV.COURSE_ID) === 0 || parseInt(__ENV.EXERCISE_ID) === 0) {
        console.log('Creating new exercise as no parameters are given');

        // Create course
        const instructorUsername = baseUsername.replace('USERID', '1');
        const instructorPassword = basePassword.replace('USERID', '1');

        addUserToInstructorsInCourse(artemisAdmin, instructorUsername, course.id);

        // Login to Artemis
        const artemis = login(instructorUsername, instructorPassword);

        // it might be necessary that the newly created groups or accounts are synced with the version control and continuous integration servers, so we wait for 1 minute
        const timeoutExercise = parseFloat(__ENV.TIMEOUT_EXERCISE);
        if (timeoutExercise > 0) {
            console.log('Wait ' + timeoutExercise + 's before creating the exam so that the setup can finish properly');
            sleep(timeoutExercise);
        }

        // Create new exercise
        exerciseId = newModelingExercise(artemis, course.id);

        sleep(2);

        return { exerciseId: exerciseId, courseId: course.id };
    } else {
        console.log('Using existing course and exercise');
        return { exerciseId: parseInt(__ENV.EXERCISE_ID), courseId: parseInt(__ENV.COURSE_ID) };
    }
}

export default function (data) {
    // The user id (1, 2, 3) is stored in __VU
    const userId = parseInt(__VU) + userOffset;
    const currentUsername = baseUsername.replace('USERID', userId);
    const currentPassword = basePassword.replace('USERID', userId);
    const artemis = login(currentUsername, currentPassword);
    const exerciseId = data.exerciseId;

    // Delay so that not all users start at the same time, batches of 3 users per second
    const delay = Math.floor(__VU / 3);
    sleep(delay * 3);

    group('Assess modeling submissions', function () {
        let participation = startTutorParticipation(artemis, exerciseId);
        if (participation) {
            const submission = getAndLockModelingSubmission(artemis, exerciseId);
            assessModelingSubmission(artemis, submissionId, submission.results[0].id);
        }
        sleep(1);
    });

    return data;
}

export function teardown(data) {
    const shouldCleanup = __ENV.CLEANUP === true || __ENV.CLEANUP === 'true';
    if (shouldCleanup) {
        const artemis = login(adminUsername, adminPassword);
        const courseId = data.courseId;
        const exerciseId = data.exerciseId;

        deleteModelingExercise(artemis, exerciseId);
        deleteCourse(artemis, courseId);
    }
}
