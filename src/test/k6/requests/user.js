import { USERS } from './endpoints.js';


export function getUser(artemis, i, baseUsername) {
    const username = baseUsername.replace('USERID', i);
    const res = artemis.get(USERS + '/' + username);
    if (res[0].status !== 200) {
        console.info('Unable to get user ' + username + ' (status: ' + res[0].status + ')!');
    }
    return res[0].body;
}

export function updateUser(artemis, user) {
    const res = artemis.put(USERS, user);
    if (res[0].status !== 200) {
        console.info('Unable to update user ' + user.login + ' (status: ' + res[0].status + ')!');
    }
}

export function newUser(artemis, i, baseUsername, basePassword, studentGroupName, instructorGroupName) {

    const username = baseUsername.replace('USERID', i);
    const password = basePassword.replace('USERID', i);
    let authorities = ["ROLE_USER"];
    if (i === 1) {
        authorities = ["ROLE_USER", "ROLE_INSTRUCTOR"];
    }

    let groups = [studentGroupName];
    if (i === 1) {
        groups = [studentGroupName, instructorGroupName];
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
        groups: groups,
        createdBy: "test-case",
    };

    console.log('Try to create new user ' + username);
    const res = artemis.post(USERS, user);
    if (res[0].status !== 201) {
        console.info('Unable to generate new user ' + username + ' (status: ' + res[0].status + ')!');
        return -1;
    }
    else {
        console.log('SUCCESS: Created new user ' + username + " with groups " + res[0].body.groups);
    }

    return JSON.parse(res[0].body).id;
}
