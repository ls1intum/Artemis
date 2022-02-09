import { fail } from 'k6';
import { nextAlphanumeric } from '../util/utils.js';
import { ASSESS_TEXT_SUBMISSION, SUBMIT_TEXT_EXAM, TEXT_EXERCISES } from './endpoints.js';

export function submitRandomTextAnswerExam(artemis, exercise, submissionId) {
    const answer = {
        id: submissionId,
        isSynced: false,
        submissionExerciseType: 'text',
        submitted: true,
        text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum',
    };

    let res = artemis.put(SUBMIT_TEXT_EXAM(exercise.id), answer);
    if (res[0].status !== 200) {
        console.log('ERROR when submitting text (Exam) via REST. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('FAILTEST: Could not submit text (Exam) via REST (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    return answer;
}

export function newTextExercise(artemis, exerciseGroup, courseID) {
    const textExercise = {
        maxPoints: 1,
        title: 'Text K6 ' + nextAlphanumeric(5),
        type: 'text',
        mode: 'INDIVIDUAL',
    };

    if (courseID) {
        textExercise.course = { id: courseId };
    }

    if (exerciseGroup) {
        textExercise.exerciseGroup = exerciseGroup;
    }

    const res = artemis.post(TEXT_EXERCISES, textExercise);
    if (res[0].status !== 201) {
        console.log('ERROR when creating a new text exercise. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('FAILTEST: Could not create text exercise (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    console.log('SUCCESS: Generated new text exercise');

    return JSON.parse(res[0].body);
}

export function assessTextSubmission(artemis, exerciseId, resultId) {
    const assessment = [
        {
            feedbacks: [
                {
                    credits: 1,
                    reference: '674aa1b614606278282b1b0d80e34f609c641972',
                    detailText: 'Good',
                    type: 'MANUAL',
                },
                {
                    credits: 2,
                    reference: '314a5bbaeb4755e0e29ee69ec4fb0f9f3aa3d2d9',
                    detailText: 'Good',
                    type: 'MANUAL',
                },
                {
                    credits: 0,
                    reference: 'bb7b48643ec0fa3b8194e196bf97692ec6e4821f',
                    detailText: 'Neutral',
                    type: 'MANUAL',
                },
                {
                    credits: -0.5,
                    reference: '21e56278b55f2dfa5c1a9d646823d177b2ac2ef0',
                    detailText: 'Negative',
                    type: 'MANUAL',
                },
            ],
            textBlocks: [
                {
                    id: '674aa1b614606278282b1b0d80e34f609c641972',
                    text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.',
                    startIndex: 0,
                    endIndex: 123,
                    numberOfAffectedSubmissions: 0,
                    type: 'AUTOMATIC',
                },
                {
                    id: '314a5bbaeb4755e0e29ee69ec4fb0f9f3aa3d2d9',
                    text: 'Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.',
                    startIndex: 124,
                    endIndex: 231,
                    numberOfAffectedSubmissions: 0,
                    type: 'AUTOMATIC',
                },
                {
                    id: 'bb7b48643ec0fa3b8194e196bf97692ec6e4821f',
                    text: 'Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.',
                    startIndex: 232,
                    endIndex: 334,
                    numberOfAffectedSubmissions: 0,
                    type: 'AUTOMATIC',
                },
                {
                    id: '21e56278b55f2dfa5c1a9d646823d177b2ac2ef0',
                    text: 'Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.',
                    startIndex: 335,
                    endIndex: 445,
                    numberOfAffectedSubmissions: 0,
                    type: 'AUTOMATIC',
                },
            ],
        },
    ];
    let res = artemis.put(ASSESS_TEXT_SUBMISSION(exerciseId, resultId), assessment);
    if (res[0].status !== 200) {
        console.log('ERROR when assessing modeling (Exercise) via REST. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('FAILTEST: Could not assess modeling (Exercise) via REST (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    return assessment;
}
