import { test } from '../support/fixtures';
import { admin } from '../support/users';
import { USER_ID, USER_ROLE, users } from '../support/users';
import { instructor, studentFour, studentOne, studentThree, studentTwo, tutor } from '../support/users';

test.describe('Setup users', () => {
    if (process.env.createUsers == 'true') {
        test.beforeEach('Creates all required users', async ({ login, userManagementAPIRequests }) => {
            await login(admin);
            for (const userKey in USER_ID) {
                const user = users.getUserWithId(USER_ID[userKey]);
                await userManagementAPIRequests.getUser(user.username).then((statusCode: number) => {
                    if (statusCode >= 200 && statusCode < 300) {
                        userManagementAPIRequests.createUser(user.username, user.password, USER_ROLE[userKey]);
                    }
                });
            }
        });
    }

    test('Logs in once with all required users', async ({ login }) => {
        // If Artemis hasn't imported the required users from Jira we have to force this by logging in with these users once
        await login(admin);
        await login(instructor);
        await login(tutor);
        await login(studentOne);
        await login(studentTwo);
        await login(studentThree);
        await login(studentFour);
    });
});
