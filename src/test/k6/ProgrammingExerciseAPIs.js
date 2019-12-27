import { group, sleep } from 'k6';
import { login } from "./requests/requests.js";
import { createExercise, startExercise, simulateSubmission, ParticipationSimulation, TestResult, deleteExercise } from "./requests/programmingExercise.js";
import { deleteCourse, newCourse } from "./requests/course.js";
import { twoSuccessfulErrorContent, allSuccessfulContent, buildErrorContent } from "./resource/constants.js";

export const options = {
    maxRedirects: 0,
    iterations: __ENV.ITERATIONS,
    vus: __ENV.ITERATIONS,
    rps: 4,
    setupTimeout: '90s',
    teardownTimeout: '90s'
};

const adminUsername = __ENV.ADMIN_USERNAME;
const adminPassword = __ENV.ADMIN_PASSWORD;
let baseUsername = __ENV.BASE_USERNAME;
let basePassword = __ENV.BASE_PASSWORD;

export function setup() {
    let artemis, exerciseId, courseId;

    // Create course
    artemis = login(adminUsername, adminPassword);
    courseId = newCourse(artemis);

    const instructorUsername = baseUsername.replace('USERID', '1');
    const instructorPassword = basePassword.replace('USERID', '1');

    // Login to Artemis
    artemis = login(instructorUsername, instructorPassword);

    // Create new exercise
    exerciseId = createExercise(artemis, courseId);

    // Wait some time for builds to finish and test results to come in
    sleep(15);

    return { exerciseId: exerciseId, courseId: courseId };
}

export default function (data) {
    // The user is randomly selected
    const userId = __VU; // Math.floor((Math.random() * maxTestUser)) + 1;
    const currentUsername = baseUsername.replace('USERID', userId);
    const currentPassword = basePassword.replace('USERID', userId);
    const artemis = login(currentUsername, currentPassword);
    const exerciseId = data.exerciseId;
    const courseId = data.courseId;

    // Delay so that not all users start at the same time, batches of 3 users per second
    const startTime = new Date().getTime();
    const delay = Math.floor(__VU / 3);
    sleep(delay * 3);

    group('Participate in Programming Exercise', function() {
        let participationId = startExercise(artemis, courseId, exerciseId);
        if (participationId) {
            // partial success, then 100%, then build error -- wait some time between submissions in order to the build server time for the result
            let simulation = new ParticipationSimulation(__ENV.TIMEOUT, exerciseId, participationId, twoSuccessfulErrorContent);
            simulateSubmission(artemis, simulation, TestResult.FAIL, '2 of 13 passed');
            simulation = new ParticipationSimulation(__ENV.TIMEOUT, exerciseId, participationId, allSuccessfulContent);
            simulateSubmission(artemis, simulation, TestResult.SUCCESS);
            simulation = new ParticipationSimulation(__ENV.TIMEOUT, exerciseId, participationId, buildErrorContent);
            simulateSubmission(artemis, simulation, TestResult.BUILD_ERROR);
        }

        const delta = (new Date().getTime() - startTime) / 1000;
        sleep(__ENV.TIMEOUT - delta);
    });

    return data;
}

export function teardown(data) {
   const artemis = login(adminUsername, adminPassword);
   const courseId = data.courseId;
   const exerciseId = data.exerciseId;

   deleteExercise(artemis, exerciseId);
   deleteCourse(artemis, courseId);
}
