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
    setupTimeout: '90s',
    teardownTimeout: '90s'
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
    let artemis, exerciseId, course, userId;

    // Create course
    artemis = login(adminUsername, adminPassword);

    course = newCourse(artemis);

    if(__ENV.CREATE_USERS) {
        console.log("Try to create " + __ENV.ITERATIONS + " users");
        for (let i = 1; i <= __ENV.ITERATIONS; i++) {
            userId = newUser(artemis, i, baseUsername, basePassword, course.studentGroupName, course.instructorGroupName);
            if (userId === -1) {
                // the creation was not successful, most probably because the user already exists, we need to update the group of the user
                updateUserWithGroup(artemis, i, baseUsername, course);
            }
        }
    }
    else {
        console.log("Do not create users, will update their groups");
        for (let i = 1; i <= __ENV.ITERATIONS; i++) {
            updateUserWithGroup(artemis, i, baseUsername, course);
        }

    }

    const instructorUsername = baseUsername.replace('USERID', '1');
    const instructorPassword = basePassword.replace('USERID', '1');

    // Login to Artemis
    artemis = login(instructorUsername, instructorPassword);

    // it might be necessary that the newly created groups or accounts are synced with the version control and continuous integration servers, so we wait for 1 minute
    sleep(60);

    // Create new exercise
    exerciseId = createExercise(artemis, course.id);

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
