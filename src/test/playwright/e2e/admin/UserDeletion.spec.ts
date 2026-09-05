import { Locator, Page, expect } from '@playwright/test';

import { test } from '../../support/fixtures';
import { UserManagementAPIRequests } from '../../support/requests/UserManagementAPIRequests';
import { admin, UserRole } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { Course } from 'app/course/shared/entities/course.model';
import { Channel } from 'app/communication/shared/entities/conversation/channel.model';
import dayjs from 'dayjs';
import textExerciseTemplate from '../../fixtures/exercise/text/template.json';

interface DeletionImpactUser {
    login: string;
    impactFingerprint: string;
}

interface DeletionImpact {
    users: DeletionImpactUser[];
}

test.describe('Retention-aware user deletion', { tag: '@fast' }, () => {
    test.describe.configure({ mode: 'serial' });

    const createdUsers = new Set<string>();
    const createdCourses = new Set<Course>();

    async function createUser(userManagementAPIRequests: UserManagementAPIRequests, suffix: string): Promise<string> {
        const login = `pw_delete_${suffix}_${generateUUID().slice(0, 8)}`;
        const response = await userManagementAPIRequests.createUser(login, login, UserRole.Student);
        expect(response.status(), `creating ${login} failed`).toBe(201);
        createdUsers.add(login);
        return login;
    }

    /**
     * Creates an account the test can sign in as. The shared helper creates an external account, which no
     * authentication provider accepts, and every other scenario here only ever acts as the administrator.
     */
    async function createSignInableUser(page: Page, suffix: string): Promise<string> {
        const login = `pw_delete_${suffix}_${generateUUID().slice(0, 8)}`;
        const response = await page.request.post('api/account/admin/users', {
            data: {
                login,
                password: login,
                firstName: login,
                lastName: login,
                email: `${login}@example.com`,
                authorities: [UserRole.Student],
                activated: true,
                internal: true,
            },
        });
        expect(response.status(), `creating ${login} failed`).toBe(201);
        createdUsers.add(login);
        return login;
    }

    /**
     * Sends a request and insists it succeeded, so a fixture step that silently did nothing fails where it happened
     * rather than as a puzzling count further down.
     */
    async function requestOk(page: Page, method: 'get' | 'post' | 'put' | 'patch', url: string, options: Parameters<typeof page.request.post>[1] = {}) {
        const response = await page.request[method](url, options);
        expect(response.ok(), `${method.toUpperCase()} ${url} failed with ${response.status()}: ${await response.text()}`).toBeTruthy();
        return response;
    }

    /** As {@link requestOk}, but returns the parsed body. */
    async function request(page: Page, method: 'get' | 'post' | 'put' | 'patch', url: string, options: Parameters<typeof page.request.post>[1] = {}) {
        return (await requestOk(page, method, url, options)).json();
    }

    /**
     * Hands in an answer as the signed-in student. The participation is started explicitly so that a fixture step that
     * silently did nothing fails where it happened rather than as a puzzling count further down.
     */
    async function startParticipationAndSubmit(page: Page, exerciseId: number, text: string): Promise<void> {
        await requestOk(page, 'post', `api/exercise/exercises/${exerciseId}/participations`);
        await requestOk(page, 'post', `api/text/exercises/${exerciseId}/text-submissions`, { data: { submissionExerciseType: 'text', text, submitted: true } });
    }

    async function searchFor(page: Page, searchTerm: string): Promise<void> {
        await page.locator('#field_searchTerm').fill(searchTerm);
        await page.getByRole('button', { name: 'Search', exact: true }).click();
    }

    function userRow(page: Page, login: string): Locator {
        return page.locator('[data-testid="user-row"]', { has: page.locator(`a[href="/admin/user-management/${login}"]`) });
    }

    async function openDeletionDialog(page: Page, login: string): Promise<Locator> {
        const impactResponse = page.waitForResponse((response) => response.url().endsWith('/api/account/admin/users/deletion-impact') && response.status() === 200);
        await userRow(page, login).getByTestId('delete-user').click();
        await impactResponse;
        const dialog = page.getByRole('dialog', { name: 'Permanently delete user data' });
        await expect(dialog).toBeVisible();
        return dialog;
    }

    test.afterEach('Delete data left by a failed scenario', async ({ login, courseManagementAPIRequests, userManagementAPIRequests }) => {
        await login(admin);
        for (const userLogin of createdUsers) {
            const userResponse = await userManagementAPIRequests.getUser(userLogin);
            if (userResponse.status() === 404) {
                continue;
            }
            // Asserted rather than ignored: this teardown swallowing a failed deletion is how the export spec's
            // cleanup could rot unnoticed until it broke, and a scenario that leaves its users behind pollutes the
            // runs after it.
            const deletionResponse = await userManagementAPIRequests.deleteUser(userLogin);
            expect(deletionResponse.ok(), `cleaning up ${userLogin} failed with ${deletionResponse.status()}: ${await deletionResponse.text()}`).toBe(true);
        }
        createdUsers.clear();
        for (const course of createdCourses) {
            await courseManagementAPIRequests.deleteCourse(course, admin);
        }
        createdCourses.clear();
    });

    test('shows the impact and permanently deletes one user only after exact confirmation', async ({ page, login, userManagementAPIRequests }) => {
        await login(admin);
        const userLogin = await createUser(userManagementAPIRequests, 'single');
        await page.goto('/admin/user-management');
        await searchFor(page, userLogin);

        const dialog = await openDeletionDialog(page, userLogin);
        await expect(dialog).toContainText('2');

        const confirmationInput = dialog.getByRole('textbox');
        const deleteButton = dialog.getByTestId('confirm-delete-users').getByRole('button');
        await expect(deleteButton).toBeDisabled();
        await confirmationInput.fill(`${userLogin}-wrong`);
        await expect(deleteButton).toBeDisabled();
        await confirmationInput.fill(userLogin);
        await expect(deleteButton).toBeEnabled();

        const deletionResponse = page.waitForResponse(
            (response) => response.url().endsWith('/api/account/admin/users') && response.request().method() === 'DELETE' && response.status() === 200,
        );
        await deleteButton.click();
        await deletionResponse;
        await expect(dialog).toBeHidden();
        expect((await page.request.get(`/api/account/admin/users/${userLogin}`)).status()).toBe(404);
        createdUsers.delete(userLogin);
    });

    test('offers deactivation without deleting the account', async ({ page, login, userManagementAPIRequests }) => {
        await login(admin);
        const userLogin = await createUser(userManagementAPIRequests, 'deactivate');
        await page.goto('/admin/user-management');
        await searchFor(page, userLogin);

        const dialog = await openDeletionDialog(page, userLogin);
        const deactivationResponse = page.waitForResponse((response) => /\/api\/account\/admin\/users\/\d+\/deactivate$/.test(response.url()) && response.status() === 200);
        await dialog.getByTestId('deactivate-users').getByRole('button').click();
        await deactivationResponse;
        await expect(dialog).toBeHidden();

        const persistedUserResponse = await page.request.get(`/api/account/admin/users/${userLogin}`);
        expect(persistedUserResponse.status()).toBe(200);
        expect((await persistedUserResponse.json()).activated).toBe(false);
    });

    test('previews not-enrolled users without deleting before confirmation', async ({ page, login, userManagementAPIRequests }) => {
        await login(admin);
        const userLogin = await createUser(userManagementAPIRequests, 'not_enrolled');
        await page.goto('/admin/user-management');

        // Only wait for the two calls the button fires; what they returned is checked separately below.
        //
        // Reading each body as its own response arrives (a .then on the waitForResponse) is not enough, though it
        // looks like it should be: Playwright still fetches the body over CDP afterwards, and the dialog these two
        // responses open re-renders the page before that lands. That variant failed in CI on this very line with
        // "No data found for resource with given identifier". Asking the API directly is what makes it reliable,
        // because an APIRequestContext buffers its body independently of what the page does next.
        const notEnrolledResponse = page.waitForResponse(
            (response) => response.url().endsWith('/api/account/admin/users/not-enrolled') && response.request().method() === 'GET' && response.status() === 200,
        );
        const impactResponse = page.waitForResponse((response) => response.url().endsWith('/api/account/admin/users/deletion-impact') && response.status() === 200);
        await page.getByRole('button', { name: 'Delete not enrolled users' }).click();
        await Promise.all([notEnrolledResponse, impactResponse]);

        const notEnrolledLogins = (await request(page, 'get', 'api/account/admin/users/not-enrolled')) as string[];
        expect(notEnrolledLogins).toContain(userLogin);
        expect(notEnrolledLogins).not.toContain('iris_bot');
        const impact = (await request(page, 'post', 'api/account/admin/users/deletion-impact', { data: { logins: notEnrolledLogins } })) as DeletionImpact;
        expect(impact.users.map((user) => user.login)).toContain(userLogin);
        const dialog = page.getByRole('dialog', { name: 'Permanently delete user data' });
        await expect(dialog).toBeVisible();
        await expect(dialog.getByTestId('confirm-delete-users').getByRole('button')).toBeDisabled();
        await dialog.getByRole('button', { name: 'Cancel' }).click();
        await expect(dialog).toBeHidden();
        expect((await page.request.get(`/api/account/admin/users/${userLogin}`)).status()).toBe(200);
    });

    test('bulk-deletes exactly the selected users after count confirmation', async ({ page, login, userManagementAPIRequests }) => {
        await login(admin);
        const commonSuffix = generateUUID().slice(0, 8);
        const firstLogin = await createUser(userManagementAPIRequests, `bulk_${commonSuffix}_a`);
        const secondLogin = await createUser(userManagementAPIRequests, `bulk_${commonSuffix}_b`);
        await page.goto('/admin/user-management');
        await searchFor(page, `pw_delete_bulk_${commonSuffix}`);

        await userRow(page, firstLogin).locator('input[type="checkbox"]').check();
        await userRow(page, secondLogin).locator('input[type="checkbox"]').check();
        const impactResponse = page.waitForResponse((response) => response.url().endsWith('/api/account/admin/users/deletion-impact') && response.status() === 200);
        await page.getByTestId('delete-selected-users').click();
        await impactResponse;

        const dialog = page.getByRole('dialog', { name: 'Permanently delete user data' });
        await expect(dialog).toBeVisible();
        await expect(dialog).toContainText('4');
        const deleteButton = dialog.getByTestId('confirm-delete-users').getByRole('button');
        await expect(deleteButton).toBeDisabled();
        await dialog.getByRole('textbox').fill('2');
        await expect(deleteButton).toBeEnabled();

        const deletionResponse = page.waitForResponse(
            (response) => response.url().endsWith('/api/account/admin/users') && response.request().method() === 'DELETE' && response.status() === 200,
        );
        await deleteButton.click();
        await deletionResponse;
        expect((await page.request.get(`/api/account/admin/users/${firstLogin}`)).status()).toBe(404);
        expect((await page.request.get(`/api/account/admin/users/${secondLogin}`)).status()).toBe(404);
        createdUsers.delete(firstLogin);
        createdUsers.delete(secondLogin);
    });

    test('deletes an account that holds data of every kind and leaves the other student data intact', async ({
        page,
        login,
        courseManagementAPIRequests,
        communicationAPIRequests,
        exerciseAPIRequests,
        examAPIRequests,
    }) => {
        await login(admin);
        const targetLogin = await createSignInableUser(page, 'rich_target');
        const bystanderLogin = await createSignInableUser(page, 'rich_bystander');
        const target = { username: targetLogin, password: targetLogin };
        const bystander = { username: bystanderLogin, password: bystanderLogin };

        // Tutorial groups are only configurable for a course that has a time zone.
        const course = await courseManagementAPIRequests.createCourse({ timeZone: 'Europe/Berlin' });
        createdCourses.add(course);
        await courseManagementAPIRequests.addStudentToCourse(course, target);
        await courseManagementAPIRequests.addStudentToCourse(course, bystander);

        // Everything the course offers a student, so that the deletion has one of each kind of row to take down.
        const channel: Channel = await communicationAPIRequests.createCourseMessageChannel(course, `deletion-${generateUUID().slice(0, 6)}`, 'Deletion', false, true);
        await communicationAPIRequests.joinUserIntoChannel(course, channel.id!, target);
        await communicationAPIRequests.joinUserIntoChannel(course, channel.id!, bystander);
        const textExercise = await exerciseAPIRequests.createTextExercise({ course });
        const teamExercise = await exerciseAPIRequests.createTextExercise({ course }, `Team ${generateUUID()}`, { ...textExerciseTemplate, mode: 'TEAM' });
        const exam = await examAPIRequests.createExam({ course });
        exam.course = course;
        const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        await exerciseAPIRequests.createTextExercise({ exerciseGroup });
        const lecture = await request(page, 'post', 'api/lecture/lectures', {
            data: { title: `Lecture ${generateUUID().slice(0, 6)}`, course, visibleDate: dayjs().subtract(1, 'day').toISOString() },
        });
        const lectureUnit = await request(page, 'post', `api/lecture/lectures/${lecture.id}/text-units`, { data: { name: 'Unit', content: 'Content', releaseDate: null } });
        await requestOk(page, 'post', `api/tutorialgroup/courses/${course.id}/tutorial-groups-configuration`, {
            data: {
                tutorialPeriodStartInclusive: dayjs().subtract(1, 'month').format('YYYY-MM-DD'),
                tutorialPeriodEndInclusive: dayjs().add(1, 'month').format('YYYY-MM-DD'),
                useTutorialGroupChannels: false,
                usePublicTutorialGroupChannels: false,
            },
        });
        const tutorialGroupId = await request(page, 'post', `api/tutorialgroup/courses/${course.id}/tutorial-groups`, {
            data: {
                title: 'Tutorial 1',
                tutorId: (await request(page, 'get', `/api/account/admin/users/${bystanderLogin}`)).id,
                language: 'ENGLISH',
                isOnline: false,
                campus: 'Garching',
                capacity: 10,
            },
        });

        // Staff-side registrations for the account under review.
        for (const student of [target, bystander]) {
            await requestOk(page, 'post', `api/exam/courses/${course.id}/exams/${exam.id}/students`, { data: [{ login: student.username }] });
        }
        await requestOk(page, 'post', `api/exam/courses/${course.id}/exams/${exam.id}/generate-missing-student-exams`);
        await requestOk(page, 'post', `api/tutorialgroup/courses/${course.id}/tutorial-groups/${tutorialGroupId}/batch-register`, { data: [targetLogin, bystanderLogin] });

        // The other student starts a thread first, so the account under review has somebody else's content to react to.
        await login(bystander);
        const bystanderThread = await communicationAPIRequests.createCourseWideMessage(course, channel.id!, 'A thread that has to survive');
        await startParticipationAndSubmit(page, textExercise.id!, 'The answer of the student who stays');

        await login(target);
        const targetThread = await communicationAPIRequests.createCourseWideMessage(course, channel.id!, 'A question from the account under review');
        await communicationAPIRequests.createCourseMessageReply(course, bystanderThread, 'A reply from the account under review');
        await requestOk(page, 'post', `api/communication/courses/${course.id}/postings/reactions`, {
            data: { emojiId: 'smiley', relatedPostId: bystanderThread.id, post: { id: bystanderThread.id, conversation: { type: 'channel', id: channel.id } } },
        });
        await requestOk(page, 'post', `api/communication/saved-posts/${bystanderThread.id}?type=post`);
        await requestOk(page, 'patch', `api/communication/courses/${course.id}/code-of-conduct/agreement`);
        await requestOk(page, 'post', `api/communication/courses/${course.id}/one-to-one-chats`, { data: [bystanderLogin] });
        await startParticipationAndSubmit(page, textExercise.id!, 'The answer of the account under review');
        await requestOk(page, 'post', `api/lecture/lectures/${lecture.id}/lecture-units/${lectureUnit.id}/completion?completed=true`);
        await requestOk(page, 'put', 'api/notification/global-notification-settings/NEW_LOGIN', { data: { enabled: false } });
        await requestOk(page, 'put', `api/notification/courses/${course.id}/setting-preset`, { data: 2 });
        await requestOk(page, 'get', 'api/calendar/subscription-token');
        await requestOk(page, 'post', 'api/core/data-exports');
        await requestOk(page, 'post', 'api/programming/ssh-settings/public-key', {
            data: { label: 'Key', publicKey: 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJmVQGKLYtBLBS1ZKMTCPeF2Nd9wJXJ1UkVOgHUYdNhU deletion@example.com' },
        });
        await requestOk(page, 'put', 'api/programming/ide-settings?programmingLanguage=JAVA', {
            data: { name: 'IntelliJ', deepLink: 'jetbrains://idea/checkout/git?idea.required.plugins.id=Pythia&checkout.repo={cloneUrl}' },
        });
        await requestOk(page, 'post', 'api/course/course-requests', {
            data: {
                title: `Requested ${generateUUID().slice(0, 6)}`,
                shortName: `req${generateUUID().slice(0, 6).replace(/-/g, '')}`,
                semester: 'WS26/27',
                testCourse: true,
                reason: 'For the deletion test',
            },
        });

        // A team the account under review owns and is the only member of.
        await login(admin);
        const targetId = (await request(page, 'get', `/api/account/admin/users/${targetLogin}`)).id;
        await requestOk(page, 'post', `api/exercise/exercises/${teamExercise.id}/teams`, {
            data: {
                name: 'Team One',
                shortName: `t${generateUUID().slice(0, 6).replace(/-/g, '')}`,
                students: [targetId],
                ownerId: targetId,
            },
        });

        await page.goto('/admin/user-management');
        await searchFor(page, targetLogin);
        const dialog = await openDeletionDialog(page, targetLogin);

        await expect(dialog).toContainText('Accounts to be deleted');
        await expect(dialog.getByTestId('deletion-account-list')).toContainText(targetLogin);
        await expect(dialog).toContainText('Continuing overrides the normal retention rules');
        // The account holds something in most of the categories the impact knows about, and the dialog has to say so.
        for (const category of [
            'Account and settings',
            'Course memberships',
            'Communication',
            'Participations, submissions, and results',
            'Exams',
            'Team memberships',
            'Tutorial groups',
            'Course requests',
            'Learning analytics',
        ]) {
            await expect(dialog, `the impact does not mention ${category}`).toContainText(category);
        }

        await dialog.getByRole('textbox').fill(targetLogin);
        const deletionResponse = page.waitForResponse(
            (response) => response.url().endsWith('/api/account/admin/users') && response.request().method() === 'DELETE' && response.status() === 200,
        );
        await dialog.getByTestId('confirm-delete-users').getByRole('button').click();
        await deletionResponse;
        expect((await page.request.get(`/api/account/admin/users/${targetLogin}`)).status()).toBe(404);
        createdUsers.delete(targetLogin);

        // The thread the account started is gone, with the reply and reaction hung on it; the other student keeps
        // their own thread, their account, and data in every category the deleted account held it in.
        await login(bystander);
        const posts: { id: number }[] = await request(
            page,
            'get',
            `api/communication/courses/${course.id}/messages?postSortCriterion=CREATION_DATE&sortingOrder=DESCENDING&conversationIds=${channel.id}`,
        );
        expect(posts.map((post) => post.id)).toContain(bystanderThread.id);
        expect(posts.map((post) => post.id)).not.toContain(targetThread.id);

        await login(admin);
        expect((await page.request.get(`/api/account/admin/users/${bystanderLogin}`)).status()).toBe(200);
        const surviving: { categories: { category: string }[] } = await request(page, 'post', '/api/account/admin/users/deletion-impact', { data: { logins: [bystanderLogin] } });
        expect(surviving.categories.map((entry) => entry.category)).toEqual(
            expect.arrayContaining(['ACCOUNT', 'COURSE_MEMBERSHIP', 'COMMUNICATION', 'PARTICIPATION', 'EXAM', 'TUTORIAL_GROUP']),
        );
    });

    test('re-previews only the surviving user after a partial deletion changes the plan', async ({ page, login, userManagementAPIRequests, courseManagementAPIRequests }) => {
        await login(admin);
        const commonSuffix = generateUUID().slice(0, 8);
        const firstLogin = await createUser(userManagementAPIRequests, `stale_${commonSuffix}_a`);
        const secondLogin = await createUser(userManagementAPIRequests, `stale_${commonSuffix}_b`);
        await page.goto('/admin/user-management');
        await searchFor(page, `pw_delete_stale_${commonSuffix}`);

        await userRow(page, firstLogin).locator('input[type="checkbox"]').check();
        await userRow(page, secondLogin).locator('input[type="checkbox"]').check();
        const initialImpactResponse = page.waitForResponse((response) => response.url().endsWith('/api/account/admin/users/deletion-impact') && response.status() === 200);
        await page.getByTestId('delete-selected-users').click();
        await initialImpactResponse;

        const course = await courseManagementAPIRequests.createCourse();
        createdCourses.add(course);
        await courseManagementAPIRequests.addStudentToCourse(course, { username: secondLogin, password: secondLogin });

        const dialog = page.getByRole('dialog', { name: 'Permanently delete user data' });
        await dialog.getByRole('textbox').fill('2');
        // As above: the deletion result is read as its response arrives. The refreshed impact request that this
        // deletion triggers is enough for Chromium to drop the deletion body before both responses are in hand.
        const deletionResultsPromise = page
            .waitForResponse((response) => response.url().endsWith('/api/account/admin/users') && response.request().method() === 'DELETE' && response.status() === 200)
            .then((response) => response.json());
        const refreshedImpactResponse = page.waitForResponse(
            (response) => response.url().endsWith('/api/account/admin/users/deletion-impact') && response.request().method() === 'POST' && response.status() === 200,
        );
        await dialog.getByTestId('confirm-delete-users').getByRole('button').click();
        const [deletionResults, refreshedImpact] = await Promise.all([deletionResultsPromise, refreshedImpactResponse]);

        expect(deletionResults).toEqual([
            { userId: expect.any(Number), login: firstLogin, status: 'DELETED', reason: null },
            { userId: expect.any(Number), login: secondLogin, status: 'PLAN_CHANGED', reason: 'impactChanged' },
        ]);
        expect(refreshedImpact.request().postDataJSON()).toEqual({ logins: [secondLogin] });
        expect((await page.request.get(`/api/account/admin/users/${firstLogin}`)).status()).toBe(404);
        await expect(userRow(page, firstLogin)).toHaveCount(0);
        createdUsers.delete(firstLogin);

        await expect(dialog.getByRole('textbox')).toHaveValue('');
        await expect(dialog).toContainText('Continuing overrides the normal retention rules');
        await expect(dialog).toContainText('Course memberships');
        await dialog.getByRole('textbox').fill(secondLogin);
        const finalDeletionResponse = page.waitForResponse(
            (response) => response.url().endsWith('/api/account/admin/users') && response.request().method() === 'DELETE' && response.status() === 200,
        );
        await dialog.getByTestId('confirm-delete-users').getByRole('button').click();
        await finalDeletionResponse;
        expect((await page.request.get(`/api/account/admin/users/${secondLogin}`)).status()).toBe(404);
        createdUsers.delete(secondLogin);
    });
});
