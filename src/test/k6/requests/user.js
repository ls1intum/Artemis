import { USERS } from './endpoints.js';

export function newUser(artemis, i) {

    const username = baseUsername.replace('USERID', i);
    const password = basePassword.replace('USERID', i);
    let authorities = ["ROLE_USER"];
    if (i === 1) {
        authorities = ["ROLE_USER", "ROLE_INSTRUCTOR"];
    }

    const user = {
        login: username,
        password: password,
        firstName: "Artemis Test " + i,
        lastName: "Artemis Test " + i,
        email: "testuser_"+i+"@tum.invalid",
        activated: true,
        langKey: "en",
        authorities: authorities,
        groups: ["artemis-test"],
        createdBy: "test-case",
    };

    console.log('Try to create new user ' + username);
    const res = artemis.post(USERS, user);
    if (res[0].status !== 201) {
        console.warn('Warning: Unable to generate new user ' + username);
    }
    else {
        console.log('SUCCESS: Created new user ' + username);
    }

    return JSON.parse(res[0].body).id;
}
