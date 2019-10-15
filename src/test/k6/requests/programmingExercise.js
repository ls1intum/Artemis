import { nextAlphanumeric } from '../util/utils.js';
import { PROGRAMMING_EXERCISES_SETUP } from './endpoints.js';
import { sleep, fail } from 'k6';
import { PARTICIPATIONS, PROGRAMMING_EXERCISE } from './endpoints.js';
import { programmingExerciseProblemStatement } from "../resource/constants.js";
import { nextWSSubscriptionId } from "../util/utils.js";
import { COMMIT } from "./endpoints.js";

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

export function simulateParticipation(artemis, timeout, exerciseId, participationId, content) {
    artemis.websocket(function (socket) {
        // Send changes via websocket
        function submitChange() {
            const contentString = JSON.stringify(content);
            const changeMessage = 'SEND\ndestination:/topic/repository/' + participationId + '/files\ncontent-length:' + contentString.length + '\n\n' + contentString + '\u0000';
            socket.send(changeMessage);
            socket.send('SUBSCRIBE\nid:sub-' + nextWSSubscriptionId() + '\ndestination:/user/topic/repository/' + participationId + '/files\n\n\u0000');
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
            subscribe(exerciseId, participationId);
        }, 5 * 1000);

        socket.setTimeout(function() {
            submitChange(participationId);
        }, 10 * 1000);

        // Commit changes
        socket.setTimeout(function() {
            artemis.post(COMMIT(participationId));
        }, 15 * 1000);

        // Wait for new result
        socket.on('message', function (message) {
            if (message.startsWith('MESSAGE\ndestination:/topic/participation/' + participationId + '/newResults')) {
                console.log(message);
                socket.close();
                console.log(`RECEIVED new result for test user ` + __VU);
            }
        });

        // Fail after timeout
        socket.setTimeout(function() {
            socket.close();
            fail('ERROR: Did not receive result for test user ' + __VU);
        }, timeout * 1000);
    });
}
