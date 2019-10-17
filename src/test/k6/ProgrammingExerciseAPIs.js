import { group, sleep } from 'k6';
import { login } from "./requests/requests.js";
import { createExercise, startExercise, simulateParticipation, ParticipationSimulation, TestResult } from "./requests/programmingExercise.js";
import { deleteCourse, newCourse } from "./requests/course.js";
import { twoSuccessfulErrorContent } from "./resource/constants.js";

export const options = {
    maxRedirects: 0,
    iterations: __ENV.ITERATIONS,
    vus: __ENV.ITERATIONS
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

    // Wait some time for builds to finish and test results to come in
    sleep(15);

    group('Participate in Programming Exercise', function() {
        let participationId = startExercise(artemis, courseId, exerciseId);
        if (participationId) {
            const simulation = new ParticipationSimulation(__ENV.TIMEOUT, exerciseId, participationId, twoSuccessfulErrorContent);
            simulateParticipation(artemis, simulation, TestResult.FAIL, '2 of 13 passed');
        }
    });

    return data;
}

export function teardown(data) {
    const artemis = login(adminUsername, adminPassword);
    const courseId = data.courseId;

    deleteCourse(artemis, courseId);
}
