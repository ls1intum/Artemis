import { login } from './requests/requests.js';
import { group, sleep } from 'k6';
import { getQuizQuestions, simulateQuizWork } from "./requests/quiz.js";

// Version: 1.1
// Creator: Firefox
// Browser: Firefox

export let options = {
    maxRedirects: 0,
    iterations: __ENV.ITERATIONS,
    vus: __ENV.ITERATIONS,
    rps: 5,
};

let baseUsername = __ENV.BASE_USERNAME;
let basePassword = __ENV.BASE_PASSWORD;

const courseId = 30;
const exerciseId = 1253;

export default function() {
    const websocketConnectionTime = __ENV.TIMEOUT; // Time in seconds the websocket is kept open, if set to 0 no websocket connection is estahblished

    // Delay so that not all users start at the same time, batches of 50 users per second
    const delay = Math.floor(__VU / 50);
    sleep(delay);

    group('Artemis Programming Exercise Participation Websocket Stresstest', function() {
        const userId = __VU;
        const currentUsername = baseUsername.replace('USERID', userId);
        const currentPassword = basePassword.replace('USERID', userId);
        const artemis = login(currentUsername, currentPassword);

        const questions = getQuizQuestions(artemis, courseId, exerciseId);
        simulateQuizWork(artemis, exerciseId, questions, websocketConnectionTime - delay);
    });
}
