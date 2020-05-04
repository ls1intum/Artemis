import { PARTICIPATION, QUIZ_EXERCISES } from './endpoints.js';
import { fail, sleep } from 'k6';
import { nextAlphanumeric, nextWSSubscriptionId, randomArrayValue } from '../util/utils.js';
import { QUIZ_EXERCISE } from './endpoints.js';

export function createQuizExercise(artemis, course) {
    let res;

    // The actual exercise
    const exercise = {
        title: 'TEST-Quiz ' + nextAlphanumeric(10),
        type: 'quiz',
        teamMode: false,
        releaseDate: null,
        randomizeQuestionOrder: true,
        presentationScoreEnabled: false,
        duration: 120,
        isActiveQuiz: false,
        isAtLeastInstructor: false,
        isAtLeastTutor: false,
        isOpenForPractice: false,
        isPlannedToStart: false,
        isPracticeModeAvailable: true,
        isVisibleBeforeStart: false,
        mode: 'INDIVIDUAL',
        course: course,
        quizQuestions: generateQuizQuestions(10),
    };

    res = artemis.post(QUIZ_EXERCISES, exercise);
    if (res[0].status !== 201) {
        console.log('ERROR when creating a new quiz exercise. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('ERROR: Could not create exercise (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    const exerciseId = JSON.parse(res[0].body).id;
    console.log('CREATED new quiz exercise, ID=' + exerciseId);

    console.log('Setting quiz to visible');
    res = artemis.put(QUIZ_EXERCISE(exerciseId) + '/set-visible');
    if (res[0].status !== 200) {
        fail('Could not set quiz to visible (' + res[0].status + ')! Response was + ' + res[0].body);
    }

    console.log('Starting quiz');
    res = artemis.put(QUIZ_EXERCISE(exerciseId) + '/start-now');
    if (res[0].status !== 200) {
        fail('Could not start quiz (' + res[0].status + ')! Response was + ' + res[0].body);
    }

    return exerciseId;
}

export function generateQuizQuestions(amount) {
    let questions = [];
    for (let i = 0; i < amount; i++) {
        let question = {
            type: 'multiple-choice',
            title: 'question' + i,
            text: 'Some question',
            scoringType: 'ALL_OR_NOTHING',
            score: 1,
            randomizeOrder: true,
            invalid: false,
            hint: 'Some question hint',
            exportQuiz: 'false',
            answerOptions: generateAnswerOptions(),
        };
        questions.push(question);
    }

    return questions;

    function generateAnswerOptions() {
        let answerOptions = [];
        let correctAnswerOption = {
            explanation: 'Correct answer explanation',
            hint: 'Correct answer hint',
            invalid: false,
            isCorrect: true,
            text: 'Correct answer option',
        };
        let wrongAnswerOption = {
            explanation: 'Wrong answer explanation',
            hint: 'Wrong answer hint',
            invalid: false,
            isCorrect: false,
            text: 'Wrong answer option',
        };

        answerOptions.push(correctAnswerOption);
        answerOptions.push(wrongAnswerOption);

        return answerOptions;
    }
}

export function deleteQuizExercise(artemis, exerciseId) {
    const res = artemis.delete(QUIZ_EXERCISE(exerciseId));
    if (res[0].status !== 200) {
        fail('Could not delete exercise (' + res[0].status + ')! Response was + ' + res[0].body);
    }
    console.log('DELETED quiz exercise, ID=' + exerciseId);
}

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
            socket.send('SUBSCRIBE\nid:sub-' + nextWSSubscriptionId() + '\ndestination:/user/topic/quizExercise/' + exerciseId + '/submission\n\n\u0000');
        }

        function submitRandomAnswer() {
            const answer = {
                submissionExerciseType: 'quiz',
                submitted: false,
                submittedAnswers: questions.map((q) => generateAnswer(q)),
            };
            const answerString = JSON.stringify(answer);
            const wsMessage = `SEND\ndestination:/topic/quizExercise/${exerciseId}/submission\ncontent-length:${answerString.length}\n\n${answerString}\u0000`;

            socket.send(wsMessage);
        }

        function generateAnswer(question) {
            const randAnswer = randomArrayValue(question.answerOptions);
            return {
                type: question.type,
                quizQuestion: question,
                selectedOptions: [randAnswer],
            };
        }

        // Subscribe to callback response from server (response after submitted answer
        socket.setTimeout(function () {
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
        socket.setTimeout(function () {
            submitRandomAnswer();
        }, 10 * 1000);

        // Stop after timeout
        socket.setTimeout(function () {
            socket.close();
        }, timeout * 1000);
    });
}
