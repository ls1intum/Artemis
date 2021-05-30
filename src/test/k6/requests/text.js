import { fail } from 'k6';
import { nextAlphanumeric } from '../util/utils.js';
import { SUBMIT_TEXT_EXAM, TEXT_EXERCISES } from './endpoints.js';

export function submitRandomTextAnswerExam(artemis, exercise, submissionId) {
    const answer = {
        id: submissionId,
        isSynced: false,
        submissionExerciseType: 'text',
        submitted: true,
        text: 'SOME RANDOM ANSWER ' + nextAlphanumeric(100),
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
        title: 'text' + nextAlphanumeric(5),
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
