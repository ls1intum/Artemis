import { group, sleep } from 'k6';
import { login } from './requests/requests.js';
import { createProgrammingExercise, startExercise, simulateSubmission, ParticipationSimulation, TestResult, deleteProgrammingExercise } from './requests/programmingExercise.js';
import { deleteCourse, newCourse } from './requests/course.js';
import { createUsersIfNeeded } from './requests/user.js';
import { allSuccessfulContentJava, buildErrorContentJava, someSuccessfulErrorContentJava } from './resource/constants_java.js';
import { allSuccessfulContentPython, buildErrorContentPython, someSuccessfulErrorContentPython } from './resource/constants_python.js';
import { allSuccessfulContentC, buildErrorContentC, someSuccessfulErrorContentC } from './resource/constants_c.js';

export const options = {
    maxRedirects: 0,
    iterations: __ENV.ITERATIONS,
    vus: __ENV.ITERATIONS,
    rps: 4,
    setupTimeout: '240s',
    teardownTimeout: '240s',
};

const adminUsername = __ENV.ADMIN_USERNAME;
const adminPassword = __ENV.ADMIN_PASSWORD;
let baseUsername = __ENV.BASE_USERNAME;
let basePassword = __ENV.BASE_PASSWORD;
let userOffset = parseInt(__ENV.USER_OFFSET);
let programmingLanguage = __ENV.PROGRAMMING_LANGUAGE;

export function setup() {
    console.log('__ENV.CREATE_USERS: ' + __ENV.CREATE_USERS);
    console.log('__ENV.TIMEOUT_PARTICIPATION: ' + __ENV.TIMEOUT_PARTICIPATION);
    console.log('__ENV.TIMEOUT_EXERCISE: ' + __ENV.TIMEOUT_EXERCISE);
    console.log('__ENV.ITERATIONS: ' + __ENV.ITERATIONS);

    let artemis, exerciseId, course, userId;

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
            console.log('Wait ' + timeoutExercise + 's before creating the programming exercise so that the setup can finish properly');
            sleep(timeoutExercise);
        }

        // Create new exercise
        exerciseId = createProgrammingExercise(artemis, course.id, programmingLanguage);

        // Wait some time for builds to finish and test results to come in
        sleep(20);

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
    const courseId = data.courseId;
    const timeoutParticipation = parseFloat(__ENV.TIMEOUT_PARTICIPATION);

    // Delay so that not all users start at the same time, batches of 3 users per second
    const startTime = new Date().getTime();
    const delay = Math.floor(__VU / 3);
    sleep(delay * 3);

    let someSuccessfulErrorContent, allSuccessfulContent, buildErrorContent, somePassedString;
    switch (programmingLanguage) {
        case 'JAVA':
            someSuccessfulErrorContent = someSuccessfulErrorContentJava;
            allSuccessfulContent = allSuccessfulContentJava;
            buildErrorContent = buildErrorContentJava;
            somePassedString = '2 of 13 passed';
            break;
        case 'PYTHON':
            someSuccessfulErrorContent = someSuccessfulErrorContentPython;
            allSuccessfulContent = allSuccessfulContentPython;
            buildErrorContent = buildErrorContentPython;
            somePassedString = '2 of 13 passed';
            break;
        case 'C':
            someSuccessfulErrorContent = someSuccessfulErrorContentC;
            allSuccessfulContent = allSuccessfulContentC;
            buildErrorContent = buildErrorContentC;
            somePassedString = '5 of 22 passed';
            break;
    }

    group('Participate in Programming Exercise', function () {
        let participationId = startExercise(artemis, courseId, exerciseId);
        if (participationId) {
            // partial success, then 100%, then build error -- wait some time between submissions in order to the build server time for the result
            let simulation = new ParticipationSimulation(timeoutParticipation, exerciseId, participationId, someSuccessfulErrorContent);
            simulateSubmission(artemis, simulation, TestResult.FAIL, somePassedString);
            simulation = new ParticipationSimulation(timeoutParticipation, exerciseId, participationId, allSuccessfulContent);
            simulateSubmission(artemis, simulation, TestResult.SUCCESS);
            simulation = new ParticipationSimulation(timeoutParticipation, exerciseId, participationId, buildErrorContent);
            if (programmingLanguage === 'C') {
                // C builds do never fail - they will only show 0/21 passed
                simulateSubmission(artemis, simulation, TestResult.FAIL, '0 of 21 passed');
            } else {
                simulateSubmission(artemis, simulation, TestResult.BUILD_ERROR);
            }
        }

        const delta = (new Date().getTime() - startTime) / 1000;
        sleep(timeoutParticipation - delta);
    });

    return data;
}

export function teardown(data) {
    const shouldCleanup = __ENV.CLEANUP === true || __ENV.CLEANUP === 'true';
    if (shouldCleanup) {
        const artemis = login(adminUsername, adminPassword);
        const courseId = data.courseId;
        const exerciseId = data.exerciseId;

        deleteProgrammingExercise(artemis, exerciseId);
        deleteCourse(artemis, courseId);
    }
}
