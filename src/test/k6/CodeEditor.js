import { login } from './requests/requests.js';
import { group, sleep } from 'k6';
import { deleteCourse, newCourse } from './requests/course.js';
import { startExercise, createExercise, deleteExercise, ParticipationSimulation, simulateSubmission, TestResult } from './requests/programmingExercise.js';
import { buildErrorContentJava } from './resource/constants_java.js';

// Version: 1.1
// Creator: Firefox
// Browser: Firefox

export let options = {
    maxRedirects: 0,
    iterations: __ENV.ITERATIONS,
    vus: __ENV.ITERATIONS,
    rps: 5,
};

const adminUsername = __ENV.ADMIN_USERNAME;
const adminPassword = __ENV.ADMIN_PASSWORD;
let baseUsername = __ENV.BASE_USERNAME;
let basePassword = __ENV.BASE_PASSWORD;

export function setup() {
    // Create course as admin
    const artemisAdmin = login(adminUsername, adminPassword);
    const courseId = newCourse(artemisAdmin).id;

    const instructorUsername = baseUsername.replace('USERID', '1');
    const instructorPassword = basePassword.replace('USERID', '1');

    // Login to Artemis
    const artemisInstructor = login(instructorUsername, instructorPassword);

    // Create new exercise
    const exerciseId = createExercise(artemisInstructor, courseId);

    return { exerciseId: exerciseId, courseId: courseId };
}

export default function (data) {
    const websocketConnectionTime = parseFloat(__ENV.TIMEOUT_PARTICIPATION); // Time in seconds the websocket is kept open, if set to 0 no websocket connection is estahblished

    // Delay so that not all users start at the same time, batches of 3 users per second
    const delay = Math.floor(__VU / 3);
    sleep(delay);

    group('Artemis Programming Exercise Participation Loadtest', function () {
        // The user is randomly selected
        const userId = __VU; // Math.floor((Math.random() * maxTestUser)) + 1;
        const currentUsername = baseUsername.replace('USERID', userId);
        const currentPassword = basePassword.replace('USERID', userId);
        const artemis = login(currentUsername, currentPassword);

        // Start exercise
        const participationId = startExercise(artemis, data.courseId, data.exerciseId);

        // Initiate websocket connection if connection time is set to value greater than 0
        if (websocketConnectionTime > 0) {
            if (participationId) {
                const simulation = new ParticipationSimulation(websocketConnectionTime, data.exerciseId, participationId, buildErrorContentJava);
                simulateSubmission(artemis, simulation, TestResult.BUILD_ERROR);
            }
            sleep(websocketConnectionTime - delay);
        }
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
