import { PARTICIPATIONS } from './endpoints';

export function startExercise(artemis, courseId, exerciseId) {
    console.log('Try to start exercise for test user ' + __VU);
    const res = artemis.post(PARTICIPATIONS(courseId, exerciseId), undefined, undefined);

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
