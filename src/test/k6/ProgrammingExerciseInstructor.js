import { group, sleep } from 'k6';
import { login } from "./requests/requests.js";
import { createExercise, deleteExercise, startExercise } from "./requests/programmingExercise.js";

export let options = {
    maxRedirects: 0,
    iterations: __ENV.ITERATIONS,
    vus: __ENV.ITERATIONS
};

let instructorUsername = "artemis_test_user_1";
let baseUsername = __ENV.BASE_USERNAME;
let basePassword = __ENV.BASE_PASSWORD;
let courseId = 45; // id of the course where the exercise is located

export function setup() {
    const instructorPassword = basePassword.replace("USERID", "1");
    let artemis, exerciseId;

    // Login to Artemis
    artemis = login(instructorUsername, instructorPassword);

    // Create new exercise
    exerciseId = createExercise(artemis, courseId);

    return { exerciseId: exerciseId };
}

export default function (data) {
    // The user is randomly selected
    let userId = __VU; // Math.floor((Math.random() * maxTestUser)) + 1;
    let currentUsername = baseUsername.replace("USERID", userId);
    let currentPassword = basePassword.replace("USERID", userId);
    let artemis = login(currentUsername, currentPassword);
    let exerciseId = data.exerciseId;

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
    const instructorPassword = basePassword.replace("USERID", "1");
    let artemis = login(instructorUsername, instructorPassword);
    let exerciseId = data.exerciseId;

    deleteExercise(artemis, exerciseId);
}
