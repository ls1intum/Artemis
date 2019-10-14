import { group, sleep } from 'k6';
import { login } from "./requests/requests.js";
import { createExercise, startExercise } from "./requests/programmingExercise.js";
import { deleteCourse, newCourse } from "./requests/course.js";

export let options = {
    maxRedirects: 0,
    iterations: __ENV.ITERATIONS,
    vus: __ENV.ITERATIONS
};

let adminUsername = __ENV.ADMIN_USERNAME;
let adminPassword = __ENV.ADMIN_PASSWORD;
let baseUsername = __ENV.BASE_USERNAME;
let basePassword = __ENV.BASE_PASSWORD;

export function setup() {
    let artemis, exerciseId, courseId;

    // Create course
    artemis = login(adminUsername, adminPassword);
    courseId = newCourse(artemis);

    const instructorUsername = baseUsername.replace("USERID", "1");
    const instructorPassword = basePassword.replace("USERID", "1");

    // Login to Artemis
    artemis = login(instructorUsername, instructorPassword);

    // Create new exercise
    exerciseId = createExercise(artemis, courseId);

    return { exerciseId: exerciseId, courseId: courseId };
}

export default function (data) {
    // The user is randomly selected
    let userId = __VU; // Math.floor((Math.random() * maxTestUser)) + 1;
    let currentUsername = baseUsername.replace("USERID", userId);
    let currentPassword = basePassword.replace("USERID", userId);
    let artemis = login(currentUsername, currentPassword);
    let exerciseId = data.exerciseId;
    let courseId = data.courseId;

    // Wait some time for builds to finish and test results to come in
    sleep(15);

    group("Participate in Programming Exercise", function() {
        let participationId = startExercise(artemis, courseId, exerciseId);
        if (participationId) {
            artemis.simulateSubmissionChanges(exerciseId, participationId, __ENV.TIMEOUT);
        }
    });

    return data;
}

export function teardown(data) {
    let artemis = login(adminUsername, adminPassword);
    let courseId = data.courseId;

    deleteCourse(artemis, courseId);
}
