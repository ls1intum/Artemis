import { test } from '../support/fixtures';
import { admin, instructor, studentFour, studentOne, studentThree, studentTwo, tutor } from '../support/users';
import { USER_ID, USER_ROLE, users } from '../support/users';

test.describe('Setup users', async () => {
    if (process.env.CREATE_USERS == 'true') {
        test.beforeEach('Creates all required users', async ({ login, userManagementAPIRequests }) => {
            await login(admin);
            for (const userKey in USER_ID) {
                const user = users.getUserWithId(USER_ID[userKey]);
                const getUserResponse = await userManagementAPIRequests.getUser(user.username);
                if (!getUserResponse.ok()) {
                    await userManagementAPIRequests.createUser(user.username, user.password, USER_ROLE[userKey]);
                }
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
