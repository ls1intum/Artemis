import { nextAlphanumeric, nextWSSubscriptionId, extractDestination } from '../util/utils.js';
import { COMMIT, NEW_FILE, PARTICIPATION_WITH_RESULT, PARTICIPATIONS, PROGRAMMING_EXERCISE, PROGRAMMING_EXERCISES_SETUP, FILES } from './endpoints.js';
import { fail, sleep } from 'k6';
import { programmingExerciseProblemStatementJava } from '../resource/constants_java.js';
import { programmingExerciseProblemStatementPython } from '../resource/constants_python.js';
import { programmingExerciseProblemStatementC } from '../resource/constants_c.js';

export function ParticipationSimulation(timeout, exerciseId, participationId, content) {
    this.timeout = timeout;
    this.exerciseId = exerciseId;
    this.participationId = participationId;
    this.newFiles = content.newFiles;
    this.content = content.content;

    this.returnsExpectedResult = function (result, expectedResult, resultString) {
        console.log('Received test result ' + result.successful + ', ' + result.resultString);

        switch (expectedResult) {
            case TestResult.SUCCESS:
                {
                    if (!result.successful) fail(`ERROR: The result for participation ${participationId} was not successful!`);
                }
                break;
            case TestResult.FAIL:
                {
                    if (result.successful || !result.hasFeedback || result.resultString !== resultString)
                        fail(`ERROR: The result for participation ${participationId} did not fail with ${resultString}! Was ${result.resultString}`);
                }
                break;
            default: {
                if (result.successful || result.hasFeedback) fail(`ERROR: The result for participation ${participationId} contained no build errors!`);
            }
        }
    };

    this.extractResultFromWebSocketMessage = function (message) {
        const resReg = /(.*\n\n)([^\u0000]*)(\u0000)/g;
        const match = resReg.exec(message);
        return JSON.parse(match[2]);
    };
}

export function getLatestResult(artemis, participationId) {
    const res = artemis.get(PARTICIPATION_WITH_RESULT(participationId));
    if (res[0].status !== 200) {
        fail('ERROR: Could not get participation information (' + res[0].status + ')! response was + ' + res[0].body);
    }

    const results = JSON.parse(res[0].body).results;
    console.log(JSON.stringify(results[results.length - 1]));

    return results[results.length - 1];
}

export const TestResult = {
    SUCCESS: 'success',
    FAIL: 'failure',
    BUILD_ERROR: 'error',
};

export function createProgrammingExercise(artemis, courseId, programmingLanguage, exerciseGroup = null) {
    let res;

    let programmingExerciseProblemStatement;
    switch (programmingLanguage) {
        case 'JAVA':
            programmingExerciseProblemStatement = programmingExerciseProblemStatementJava;
            break;
        case 'PYTHON':
            programmingExerciseProblemStatement = programmingExerciseProblemStatementPython;
            break;
        case 'C':
            programmingExerciseProblemStatement = programmingExerciseProblemStatementC;
            break;
    }

    // The actual exercise
    const exercise = {
        title: 'TEST ' + nextAlphanumeric(10),
        shortName: 'TEST' + nextAlphanumeric(5).toUpperCase(),
        maxScore: 42,
        assessmentType: 'AUTOMATIC',
        type: 'programming',
        programmingLanguage: programmingLanguage,
        allowOnlineEditor: true,
        packageName: 'de.test',
        problemStatement: programmingExerciseProblemStatement,
        presentationScoreEnabled: false,
        staticCodeAnalysisEnabled: false,
        sequentialTestRuns: false,
        mode: 'INDIVIDUAL',
        projectType: programmingLanguage === 'JAVA' ? 'ECLIPSE' : undefined,
    };

    if (courseId) {
        exercise.course = {
            id: courseId,
        };
    }
    if (exerciseGroup) {
        exercise.exerciseGroup = exerciseGroup;
    }

    res = artemis.post(PROGRAMMING_EXERCISES_SETUP, exercise);
    if (res[0].status !== 201) {
        console.log('ERROR when creating a new programming exercise. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('ERROR: Could not create exercise (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    const exerciseId = JSON.parse(res[0].body).id;
    console.log('CREATED new programming exercise, ID=' + exerciseId);

    return exerciseId;
}

export function deleteProgrammingExercise(artemis, exerciseId) {
    const res = artemis.delete(PROGRAMMING_EXERCISE(exerciseId), {
        deleteStudentReposBuildPlans: true,
        deleteBaseReposBuildPlans: true,
    });
    if (res[0].status !== 200) {
        fail('Could not delete exercise (' + res[0].status + ')! Response was + ' + res[0].body);
    }
    console.log('DELETED programming exercise, ID=' + exerciseId);
}

export function startExercise(artemis, courseId, exerciseId) {
    console.log('Try to start exercise for test user ' + __VU);
    const res = artemis.post(PARTICIPATIONS(courseId, exerciseId), null, null);
    // console.log('RESPONSE of starting exercise: ' + res[0].body);

    if (res[0].status === 400) {
        sleep(3000);
        return;
    }

    if (res[0].status !== 201) {
        fail('ERROR trying to start exercise for test user ' + __VU + ':\n #####ERROR (' + res[0].status + ')##### ' + res[0].body);
    } else {
        console.log('SUCCESSFULLY started exercise for test user ' + __VU);
    }

    return JSON.parse(res[0].body).id;
}

export function createNewFile(artemis, participationId, filename) {
    const res = artemis.post(NEW_FILE(participationId), null, { file: filename });

    if (res[0].status !== 200) {
        fail('ERROR: Unable to create new file ' + filename);
    }
}

export function updateFileContent(artemis, participationId, content) {
    const res = artemis.put(FILES(participationId), content);

    if (res[0].status !== 200) {
        fail('ERROR: Unable to update file content for participation' + participationId);
    }
}

export function simulateSubmission(artemis, participationSimulation, expectedResult, resultString) {
    // First, we have to create all new files
    participationSimulation.newFiles.forEach((file) => createNewFile(artemis, participationSimulation.participationId, file));

    artemis.websocket(function (socket) {
        // Send changes via websocket - DEPRECATED AS OF 2020-07-13, now REST is used (see commit e6038467a8cc5ea29cb0b50b0bb2d2c51c94d348)
        function submitChange(content) {
            const contentString = JSON.stringify(content);
            const changeMessage =
                'SEND\ndestination:/topic/repository/' +
                participationSimulation.participationId +
                '/files\ncontent-length:' +
                contentString.length +
                '\n\n' +
                contentString +
                '\u0000';
            socket.send(changeMessage);
            socket.send('SUBSCRIBE\nid:sub-' + nextWSSubscriptionId() + '\ndestination:/user/topic/repository/' + participationSimulation.participationId + '/files\n\n\u0000');
        }

        // Subscribe to new results and participations
        function subscribe(exerciseId, participationId) {
            socket.send('SUBSCRIBE\nid:sub-' + nextWSSubscriptionId() + '\ndestination:/user/topic/newResults\n\n\u0000');
            socket.send('SUBSCRIBE\nid:sub-' + nextWSSubscriptionId() + '\ndestination:/user/topic/exercise/' + exerciseId + '/participation\n\n\u0000');
        }

        // Ping every 10 secs
        socket.setInterval(function timeout() {
            socket.ping();
        }, 10000);

        socket.setTimeout(function () {
            subscribe(participationSimulation.exerciseId, participationSimulation.participationId);
        }, 5 * 1000);

        socket.setTimeout(function () {
            // submitChange(participationSimulation.content);
            updateFileContent(artemis, participationSimulation.participationId, participationSimulation.content);
            console.log('SEND file data for test user ' + __VU);
        }, 10 * 1000);

        // Commit changes
        socket.setTimeout(function () {
            artemis.post(COMMIT(participationSimulation.participationId));
            console.log('COMMIT changes for test user ' + __VU);
        }, 15 * 1000);

        // Wait for new result
        socket.on('message', function (message) {
            if (message.startsWith('MESSAGE\n') && extractDestination(message) === '/user/topic/newResults') {
                socket.close();
                const result = participationSimulation.extractResultFromWebSocketMessage(message);
                participationSimulation.returnsExpectedResult(result, expectedResult, resultString);
                console.log(`RECEIVE new result for test user ` + __VU);
            }
        });

        // Fail after timeout
        socket.setTimeout(function () {
            socket.close();
            // Try to GET latest result
            console.log('Websocket timed out, trying to GET now');
            const result = getLatestResult(artemis, participationSimulation.participationId);
            if (result !== undefined) {
                participationSimulation.returnsExpectedResult(result, expectedResult, resultString);
            } else {
                fail('ERROR: Did not receive result for test user ' + __VU);
            }
        }, participationSimulation.timeout * 1000);
    });
}
