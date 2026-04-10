import { test } from '../support/fixtures';
import { admin, instructor, studentFour, studentOne, studentThree, studentTwo, tutor } from '../support/users';

test.describe('Verify seed users', async () => {
    test('All seed users can authenticate', async ({ login }) => {
        await login(admin);
        await login(instructor);
        await login(tutor);
        await login(studentOne);
        await login(studentTwo);
        await login(studentThree);
        await login(studentFour);
    });
});
