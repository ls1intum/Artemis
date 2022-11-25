import { extractDestination, nextAlphanumeric, nextWSSubscriptionId } from '../util/utils.js';
import { COMMIT, FILES, NEW_FILE, PARTICIPATION_WITH_RESULT, PROGRAMMING_EXERCISE, PROGRAMMING_EXERCISES_SETUP, SCA_CATEGORIES } from './endpoints.js';
import { fail } from 'k6';
import { programmingExerciseProblemStatementJava } from '../resource/constants_java.js';
import { programmingExerciseProblemStatementPython } from '../resource/constants_python.js';
import { programmingExerciseProblemStatementC } from '../resource/constants_c.js';

export function ParticipationSimulation(timeout, exerciseId, participationId, content) {
    this.timeout = timeout;
    this.exerciseId = exerciseId;
    this.participationId = participationId;
    this.newFiles = content.newFiles;
    this.content = content.content;

    this.returnsExpectedResult = function (result, expectedResult) {
        console.log('Received test result ' + result.successful);

        switch (expectedResult) {
            case TestResult.SUCCESS:
                {
                    if (!result.successful) fail('FAILTEST: The result for participation ' + participationId + ' was not successful!');
                }
                break;
            case TestResult.FAIL:
                {
                    if (result.successful) fail('FAILTEST: The result for participation ' + participationId + ' did not fail!');
                }
                break;
            default: {
                if (result.successful) fail('FAILTEST: The result for participation ' + participationId + ' contained no build errors!');
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
        fail('FAILTEST: Could not get participation information (' + res[0].status + ')! response was + ' + res[0].body);
    }

    const results = JSON.parse(res[0].body).results;
    if (!results || results.length === 0) {
        fail('FAILTEST: Did not receive result for test user ' + __VU);
    }
    const lastResult = results[results.length - 1];
    console.log(JSON.stringify(lastResult));

    return lastResult;
}

export const TestResult = {
    SUCCESS: 'success',
    FAIL: 'failure',
    BUILD_ERROR: 'error',
};

export function createProgrammingExercise(artemis, courseId, exerciseGroup = undefined, programmingLanguage, enableSCA = false) {
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
        title: 'TEST K6' + nextAlphanumeric(10),
        shortName: 'TESTK6' + nextAlphanumeric(5).toUpperCase(),
        maxPoints: 42,
        assessmentType: 'AUTOMATIC',
        type: 'programming',
        programmingLanguage: programmingLanguage,
        allowOnlineEditor: true,
        packageName: 'de.test',
        problemStatement: programmingExerciseProblemStatement,
        presentationScoreEnabled: false,
        staticCodeAnalysisEnabled: enableSCA,
        sequentialTestRuns: false,
        mode: 'INDIVIDUAL',
        projectType: programmingLanguage === 'JAVA' ? 'PLAIN_MAVEN' : undefined,
    };

    if (courseId) {
        exercise.course = { id: courseId };
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
        fail('FAILTEST: Could not create exercise (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    const exerciseId = JSON.parse(res[0].body).id;
    console.log('CREATED new programming exercise, ID=' + exerciseId);

    return exerciseId;
}

export function getScaCategories(artemis, exerciseId) {
    const res = artemis.get(SCA_CATEGORIES(exerciseId));
    if (res[0].status !== 200) {
        fail('FAILTEST: Could not get SCA categories (' + res[0].status + ')! Response was + ' + res[0].body);
    }
    console.log('GET SCA categories for programming exercise with id=' + exerciseId);
    return JSON.parse(res[0].body);
}

export function configureScaCategories(artemis, exerciseId, scaCategories, programmingLanguage) {
    // Find and prepare categories for the configuration update
    let patchedCategories;
    switch (programmingLanguage) {
        case 'JAVA':
            let badPracticeCategory = scaCategories.find((category) => category.name === 'Bad Practice');
            if (!badPracticeCategory) {
                fail(`FAILTEST: Could not find SCA category "Bad Practice" for exercise: ${exerciseId}`);
            }
            patchedCategories = [
                {
                    id: badPracticeCategory.id,
                    penalty: 1,
                    maxPenalty: 3,
                    state: 'GRADED',
                },
            ];
    }

    const res = artemis.patch(SCA_CATEGORIES(exerciseId), patchedCategories);
    if (res[0].status !== 200) {
        fail('FAILTEST: Could not patch SCA categories (' + res[0].status + ')! Response was + ' + res[0].body);
    }
    console.log('PATCHED SCA categories for programming exercise with id=' + exerciseId);
}

export function deleteProgrammingExercise(artemis, exerciseId) {
    const res = artemis.delete(PROGRAMMING_EXERCISE(exerciseId), {
        deleteStudentReposBuildPlans: true,
        deleteBaseReposBuildPlans: true,
    });
    if (res[0].status !== 200) {
        fail('FAILTEST: Could not delete exercise (' + res[0].status + ')! Response was + ' + res[0].body);
    }
    console.log('DELETED programming exercise, ID=' + exerciseId);
}

export function createNewFile(artemis, participationId, filename) {
    const res = artemis.post(NEW_FILE(participationId), undefined, { file: filename });

    if (res[0].status !== 200) {
        fail('FAILTEST: Unable to create new file ' + filename);
    }
}

function subscribe(socket, exerciseId) {
    socket.send('SUBSCRIBE\nid:sub-' + nextWSSubscriptionId() + '\ndestination:/user/topic/newResults\n\n\u0000');
    socket.send('SUBSCRIBE\nid:sub-' + nextWSSubscriptionId() + '\ndestination:/user/topic/exercise/' + exerciseId + '/participation\n\n\u0000');
}

function updateFileContent(artemis, participationId, content) {
    const res = artemis.put(FILES(participationId), content);

    if (res[0].status !== 200) {
        fail('FAILTEST: Unable to update file content for participation' + participationId);
    }
}

export function simulateSubmission(artemis, participationSimulation, expectedResult) {
    // First, we have to create all new files
    if (participationSimulation.newFiles) {
        participationSimulation.newFiles.forEach((file) => createNewFile(artemis, participationSimulation.participationId, file));
    }

    artemis.websocket(function (socket) {
        // Subscribe to new results and participations
        socket.setTimeout(function () {
            subscribe(socket, participationSimulation.exerciseId);
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
                participationSimulation.returnsExpectedResult(result, expectedResult);
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
                participationSimulation.returnsExpectedResult(result, expectedResult);
            } else {
                fail('FAILTEST: Did not receive result for test user ' + __VU);
            }
        }, participationSimulation.timeout * 1000);
    });
}
