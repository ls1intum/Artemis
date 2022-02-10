import { PARTICIPATIONS, TUTOR_PARTICIPATIONS } from './endpoints.js';

export function startExercise(artemis, exerciseId) {
    console.log('Try to start exercise for test user ' + __VU);
    const res = artemis.post(PARTICIPATIONS(exerciseId), undefined, undefined);

    if (res[0].status === 400) {
        sleep(3000);
        return;
    }

    if (res[0].status !== 201) {
        fail('FAILTEST: error trying to start exercise for test user ' + __VU + ':\n #####ERROR (' + res[0].status + ')##### ' + res[0].body);
    } else {
        console.log('SUCCESSFULLY started exercise for test user ' + __VU);
    }

    return JSON.parse(res[0].body);
}

export function getExercise(artemis, exerciseId, endpoint) {
    const res = artemis.get(endpoint);
    console.log('Server response is ' + JSON.stringify(res));
    if (res[0].status !== 200) {
        console.log('ERROR when getting existing exercise. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('FAILTEST: Could not get exercise (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    console.log('SUCCESS: Get existing exercise');

    return JSON.parse(res[0].body);
}

export function deleteExercise(artemis, exerciseId, endpoint) {
    const res = artemis.delete(endpoint);
    if (res[0].status !== 200) {
        fail('FAILTEST: Could not delete exercise (' + res[0].status + ')! Response was + ' + res[0].body);
    }
    console.log('DELETED modeling exercise, ID=' + exerciseId);
}

export function startTutorParticipation(artemis, exerciseId) {
    const res = artemis.post(TUTOR_PARTICIPATIONS(exerciseId), { status: 'NOT_PARTICIPATED' });
    if (res[0].status !== 201) {
        fail('FAILTEST: error trying to start tutor participation for test user ' + __VU + ':\n #####ERROR (' + res[0].status + ')##### ' + res[0].body);
    } else {
        console.log('SUCCESSFULLY started tutor participation for test user ' + __VU);
    }

    return JSON.parse(res[0].body);
}

export function getAndLockSubmission(artemis, exerciseId, endpoint) {
    const res = artemis.get(endpoint);
    if (res[0].status !== 200) {
        fail('FAILTEST: Could not get submission without assessment (' + res[0].status + ')! Response was + ' + res[0].body);
    }
    return JSON.parse(res[0].body);
}
