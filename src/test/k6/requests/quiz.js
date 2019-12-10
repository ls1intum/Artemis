import { PARTICIPATION } from "./endpoints.js";
import { fail, sleep } from 'k6';
import { nextWSSubscriptionId, randomArrayValue } from "../util/utils.js";

export function getQuizQuestions(artemis, courseId, exerciseId) {
    const res = artemis.get(PARTICIPATION(exerciseId));
    if (res[0].status !== 200) {
        fail('ERROR: Could not get quiz information (' + res[0].status + ')! response was + ' + res[0].body);
    }

    return JSON.parse(res[0].body).exercise.quizQuestions;
}

export function simulateQuizWork(artemis, exerciseId, questions, timeout) {
    artemis.websocket(function (socket) {
        function subscribe() {
            socket.send('SUBSCRIBE\nid:sub-' + nextWSSubscriptionId() + '\ndestination:/user/topic/quizExercise/' + exerciseId +'/submission\n\n\u0000');
        }

        function submitRandomAnswer() {
            const randAnswer = randomArrayValue(questions[0].answerOptions);
            const answer = {
                submissionExerciseType: 'quiz',
                submitted: false,
                submittedAnswers: [{
                    type: questions[0].type,
                    quizQuestions: questions[0],
                    selectedOptions: [randAnswer]
                }]
            };
            const answerString = JSON.stringify(answer);
            const wsMessage = `SEND\ndestination:/topic/quizExercise/${exerciseId}/submission\ncontent-length:${answerString.length}\n\n${answerString}\u0000`;

            socket.send(wsMessage);
        }

        // Subscribe to callback response from server (response after submitted answer
        socket.setTimeout(function() {
            subscribe();
        }, 5 * 1000);

        // Wait for new result
        socket.on('message', function (message) {
            if (message.startsWith('MESSAGE\ndestination:/user/topic/quizExercise/' + exerciseId + '/submission')) {
                console.log(`RECEIVED callback from server for ${__VU}`);
                sleep(5);
                socket.close();
            }
        });

        // submit new quiz answer
        socket.setTimeout(function() {
            submitRandomAnswer();
        }, 10 * 1000);

        // Stop after timeout
        socket.setTimeout(function() {
            socket.close();
        }, timeout * 1000);
    })
}
