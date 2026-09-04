import { Locator, Page, expect } from '@playwright/test';

import { test } from '../../support/fixtures';
import { UserManagementAPIRequests } from '../../support/requests/UserManagementAPIRequests';
import { admin, UserRole } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { Course } from 'app/course/shared/entities/course.model';

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

    test.afterEach('Delete data left by a failed scenario', async ({ page, login, courseManagementAPIRequests }) => {
        await login(admin);
        for (const userLogin of createdUsers) {
            const userResponse = await page.request.get(`/api/account/admin/users/${userLogin}`);
            if (userResponse.status() === 404) {
                continue;
            }
            const impactResponse = await page.request.post('/api/account/admin/users/deletion-impact', { data: { logins: [userLogin] } });
            if (!impactResponse.ok()) {
                continue;
            }
            const impact = (await impactResponse.json()) as DeletionImpact;
            await page.request.delete('/api/account/admin/users', {
                data: { users: impact.users.map((user) => ({ login: user.login, impactFingerprint: user.impactFingerprint })) },
            });
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

        const notEnrolledResponse = page.waitForResponse(
            (response) => response.url().endsWith('/api/account/admin/users/not-enrolled') && response.request().method() === 'GET' && response.status() === 200,
        );
        const impactResponse = page.waitForResponse((response) => response.url().endsWith('/api/account/admin/users/deletion-impact') && response.status() === 200);
        await page.getByRole('button', { name: 'Delete not enrolled users' }).click();
        const [notEnrolled, impact] = await Promise.all([notEnrolledResponse, impactResponse]);

        const notEnrolledLogins = (await notEnrolled.json()) as string[];
        expect(notEnrolledLogins).toContain(userLogin);
        expect(notEnrolledLogins).not.toContain('iris_bot');
        expect(((await impact.json()) as DeletionImpact).users.map((user) => user.login)).toContain(userLogin);
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
        const deletionResponse = page.waitForResponse(
            (response) => response.url().endsWith('/api/account/admin/users') && response.request().method() === 'DELETE' && response.status() === 200,
        );
        const refreshedImpactResponse = page.waitForResponse(
            (response) => response.url().endsWith('/api/account/admin/users/deletion-impact') && response.request().method() === 'POST' && response.status() === 200,
        );
        await dialog.getByTestId('confirm-delete-users').getByRole('button').click();
        const [deletion, refreshedImpact] = await Promise.all([deletionResponse, refreshedImpactResponse]);

        expect(await deletion.json()).toEqual([
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
