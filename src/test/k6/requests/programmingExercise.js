import { nextAlphanumeric, nextWSSubscriptionId } from '../util/utils.js';
import { PROGRAMMING_EXERCISES_SETUP, COMMIT, PARTICIPATIONS, PROGRAMMING_EXERCISE, NEW_FILE } from './endpoints.js';
import { sleep, fail } from 'k6';
import { programmingExerciseProblemStatement } from "../resource/constants.js";

export function ParticipationSimulation(timeout, exerciseId, participationId, content) {
    this.timeout = timeout;
    this.exerciseId = exerciseId;
    this.participationId = participationId;
    this.newFiles = content.newFiles;
    this.content = content.content;

    this.returnsExpectedResult = function(message, expectedResult, resultString) {
        const resReg = /(.*\n\n)([^\u0000]*)(\u0000)/g;
        const match = resReg.exec(message);
        const result = JSON.parse(match[2]);

        switch (expectedResult) {
            case TestResult.SUCCESS: {
                if(!result.successful) fail(`ERROR: The result for participation ${participationId} was not successful!`);
            }
            break;
            case TestResult.FAIL: {
                if(result.successful || !result.hasFeedback || result.resultString !== resultString)
                    fail(`ERROR: The result for participation ${participationId} did not fail with ${resultString}! Was ${result.resultString}`)
            }
            break;
            default: {
                if(result.successful || result.hasFeedback) fail(`ERROR: The result for participation ${participationId} contained no build errors!`)
            }
        }
    }
}

export const TestResult = {
    SUCCESS: 'success',
    FAIL: 'failure',
    BUILD_ERROR: 'error'
};

export function createExercise(artemis, courseId) {
    let res;

    // The actual exercise
    const exercise = {
        title: 'TEST ' + nextAlphanumeric(10),
        shortName: 'TEST'+ nextAlphanumeric(5).toUpperCase(),
        maxScore: 42,
        assessmentType: 'AUTOMATIC',
        type: 'programming',
        programmingLanguage: 'JAVA',
        publishBuildPlanUrl: true,
        allowOnlineEditor: true,
        packageName: 'de.test',
        problemStatement: programmingExerciseProblemStatement,
        presentationScoreEnabled: false,
        sequentialTestRuns: true,
        mode: 'INDIVIDUAL',
        course: {
            id: courseId
        }
    };

    res = artemis.post(PROGRAMMING_EXERCISES_SETUP, exercise);
    if (res[0].status !== 201) {
        fail('ERROR: Could not create exercise (' + res[0].status + ')! response was + ' + res[0].body);
    }
    const exerciseId = JSON.parse(res[0].body).id;
    console.log('CREATED new programming exercise, ID=' + exerciseId);

    return exerciseId;
}

export function deleteExercise(artemis, exerciseId) {
    const res = artemis.delete(PROGRAMMING_EXERCISE(exerciseId), {
        deleteStudentReposBuildPlans: true,
        deleteBaseReposBuildPlans: true
    });
    if (res[0].status !== 200) {
        fail('Could not delete exercise (' + res[0].status + ')! Response was + ' + res[0].body);
    }
    console.log('DELETED programming exercise, ID=' + exerciseId);
}

export function startExercise(artemis, courseId, exerciseId) {
    const res = artemis.post(PARTICIPATIONS(courseId, exerciseId), null, null);
    // console.log('RESPONSE of starting exercise: ' + res[0].body);

    if (res[0].status === 400) {
        sleep(3000);
        return;
    }

    if (res[0].status !== 201) {
        fail('ERROR trying to start exercise for ' + __VU + ':\n #####ERROR (' + res[0].status + ')##### ' + res[0].body);
    } else {
        console.log('SUCCESSFULLY started exercise for ' + __VU);
    }

    return JSON.parse(res[0].body).id;
}

export function createNewFile(artemis, participationId, filename) {
    const res = artemis.post(NEW_FILE(participationId), null, { file: filename });

    if (res[0].status !== 200) {
        fail('ERROR: Unable to create new file ' + filename);
    }
}

export function simulateSubmission(artemis, participationSimulation, expectedResult, resultString) {
    // First, we have to create all new files
    participationSimulation.newFiles.forEach(file => createNewFile(artemis, participationSimulation.participationId, file));

    artemis.websocket(function (socket) {
        // Send changes via websocket
        function submitChange(content) {
            const contentString = JSON.stringify(content);
            const changeMessage = 'SEND\ndestination:/topic/repository/' + participationSimulation.participationId + '/files\ncontent-length:' + contentString.length + '\n\n' + contentString + '\u0000';
            socket.send(changeMessage);
            socket.send('SUBSCRIBE\nid:sub-' + nextWSSubscriptionId() + '\ndestination:/user/topic/repository/' + participationSimulation.participationId + '/files\n\n\u0000');
        }

        // Subscribe to new results and participations
        function subscribe(exerciseId, participationId) {
            socket.send('SUBSCRIBE\nid:sub-' + nextWSSubscriptionId() + '\ndestination:/topic/participation/' + participationId +'/newResults\n\n\u0000');
            socket.send('SUBSCRIBE\nid:sub-' + nextWSSubscriptionId() + '\ndestination:/user/topic/exercise/' + exerciseId + '/participation\n\n\u0000');
        }

        // Ping every 10 secs
        socket.setInterval(function timeout() {
            socket.ping();
        }, 10000);

        socket.setTimeout(function() {
            subscribe(participationSimulation.exerciseId, participationSimulation.participationId);
        }, 5 * 1000);

        socket.setTimeout(function() {
            submitChange(participationSimulation.content);
            console.log('USED WS for sending file data for ' + __VU)
        }, 10 * 1000);

        // Commit changes
        socket.setTimeout(function() {
            artemis.post(COMMIT(participationSimulation.participationId));
            console.log('COMMITTED changes for ' + __VU);
        }, 15 * 1000);

        // Wait for new result
        socket.on('message', function (message) {
            if (message.startsWith('MESSAGE\ndestination:/topic/participation/' + participationSimulation.participationId + '/newResults')) {
                socket.close();
                participationSimulation.returnsExpectedResult(message, expectedResult, resultString);
                console.log(`RECEIVED new result for test user ` + __VU);
            }
        });

        // Fail after timeout
        socket.setTimeout(function() {
            socket.close();
            fail('ERROR: Did not receive result for test user ' + __VU);
        }, participationSimulation.timeout * 1000);
    });
}
