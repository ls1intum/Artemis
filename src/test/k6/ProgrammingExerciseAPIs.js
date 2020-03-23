import { group, sleep } from 'k6';
import { login } from "./requests/requests.js";
import { createExercise, startExercise, simulateSubmission, ParticipationSimulation, TestResult, deleteExercise } from "./requests/programmingExercise.js";
import { deleteCourse, newCourse, addUserToStudentsInCourse, addUserToInstructorsInCourse } from "./requests/course.js";
import { newUser, getUser, updateUser } from './requests/user.js';
import { twoSuccessfulErrorContent, allSuccessfulContent, buildErrorContent } from "./resource/constants.js";

export const options = {
    maxRedirects: 0,
    iterations: __ENV.ITERATIONS,
    vus: __ENV.ITERATIONS,
    rps: 4,
    setupTimeout: '120s',
    teardownTimeout: '120s'
};

const adminUsername = __ENV.ADMIN_USERNAME;
const adminPassword = __ENV.ADMIN_PASSWORD;
let baseUsername = __ENV.BASE_USERNAME;
let basePassword = __ENV.BASE_PASSWORD;

export function updateUserWithGroup(artemis, i, baseUsername, course) {

    const username = baseUsername.replace('USERID', i);
    addUserToStudentsInCourse(artemis, username, course.id);

    if (i === 1) {
        addUserToInstructorsInCourse(artemis, username, course.id);
    }
}

export function setup() {

    console.log("__ENV.CREATE_USERS: " + __ENV.CREATE_USERS);
    console.log("__ENV.TIMEOUT_PARTICIPATION: " + __ENV.TIMEOUT_PARTICIPATION);
    console.log("__ENV.TIMEOUT_EXERCISE: " + __ENV.TIMEOUT_EXERCISE);
    console.log("__ENV.ITERATIONS: " + __ENV.ITERATIONS);

    let artemis, exerciseId, course, userId;
    const iterations = parseInt(__ENV.ITERATIONS);

    // Create course
    artemis = login(adminUsername, adminPassword);

    course = newCourse(artemis);

    if(__ENV.CREATE_USERS === true || __ENV.CREATE_USERS === 'true') {
        console.log("Try to create " + iterations + " users");
        for (let i = 1; i <= iterations; i++) {
            userId = newUser(artemis, i, baseUsername, basePassword, course.studentGroupName, course.instructorGroupName);
            if (userId === -1) {
                // the creation was not successful, most probably because the user already exists, we need to update the group of the user
                updateUserWithGroup(artemis, i, baseUsername, course);
            }
        }
    }
    else {
        console.log("Do not create users, assume the user exists in the external system, will update their groups");
        for (let i = 1; i <= iterations; i++) {
            // we need to login once with the user, so that the user is synced and available for the update with the groups
            login(baseUsername.replace('USERID', i), basePassword.replace('USERID', i))
        }
        artemis = login(adminUsername, adminPassword);
        for (let i = 1; i <= iterations; i++) {
            updateUserWithGroup(artemis, i, baseUsername, course);
        }

    }

    const instructorUsername = baseUsername.replace('USERID', '1');
    const instructorPassword = basePassword.replace('USERID', '1');

    // Login to Artemis
    artemis = login(instructorUsername, instructorPassword);

    // it might be necessary that the newly created groups or accounts are synced with the version control and continuous integration servers, so we wait for 1 minute
    const timeoutExercise = parseFloat(__ENV.TIMEOUT_EXERCISE);
    if (timeoutExercise > 0) {
        console.log("Wait " + timeoutExercise + "s before creating the programming exercise so that the setup can finish properly");
        sleep(timeoutExercise);
    }

    // Create new exercise
    exerciseId = createExercise(artemis, course.id);

    // Wait some time for builds to finish and test results to come in
    sleep(30);

    return { exerciseId: exerciseId, courseId: course.id };
}

export default function (data) {
    // The user id (1, 2, 3) is stored in __VU
    const userId = __VU;
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

    group('Participate in Programming Exercise', function() {
        let participationId = startExercise(artemis, courseId, exerciseId);
        if (participationId) {
            // partial success, then 100%, then build error -- wait some time between submissions in order to the build server time for the result
            let simulation = new ParticipationSimulation(timeoutParticipation, exerciseId, participationId, twoSuccessfulErrorContent);
            simulateSubmission(artemis, simulation, TestResult.FAIL, '2 of 13 passed');
            simulation = new ParticipationSimulation(timeoutParticipation, exerciseId, participationId, allSuccessfulContent);
            simulateSubmission(artemis, simulation, TestResult.SUCCESS);
            simulation = new ParticipationSimulation(timeoutParticipation, exerciseId, participationId, buildErrorContent);
            simulateSubmission(artemis, simulation, TestResult.BUILD_ERROR);
        }

        const delta = (new Date().getTime() - startTime) / 1000;
        sleep(timeoutParticipation - delta);
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
