import { createServer, Server } from 'node:http';
import { AddressInfo } from 'node:net';
import { APIRequest, APIRequestContext, Page, test, expect } from '@playwright/test';
import { createAuthenticatedApiContext } from '../../support/authenticatedApiContext';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExamExerciseGroupCreationPage } from '../../support/pageobjects/exam/ExamExerciseGroupCreationPage';
import { ExerciseAPIRequests } from '../../support/requests/ExerciseAPIRequests';
import { ExerciseType } from '../../support/constants';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

/** Exercises real Playwright cookie jars without depending on an Artemis installation. */
test.describe('Isolated exam API authentication', () => {
    let server: Server;
    let baseURL: string;
    let deletionCookies: string[];
    let account: { login: string; authorities: string[] };
    let authenticationStatus: number;
    let deletionStatuses: number[];

    test.beforeEach(async () => {
        deletionCookies = [];
        account = { login: 'admin', authorities: ['ROLE_ADMIN'] };
        authenticationStatus = 200;
        deletionStatuses = [];
        server = createServer((request, response) => {
            if (request.url === '/api/core/public/authenticate') {
                response.statusCode = authenticationStatus;
                response.setHeader('Set-Cookie', 'jwt=admin; Path=/; HttpOnly');
                response.end('{}');
                return;
            }
            if (request.url === '/api/core/public/account') {
                response.setHeader('Content-Type', 'application/json');
                response.end(JSON.stringify(account));
                return;
            }
            if (request.url === '/authenticate/admin' || request.url === '/authenticate/student') {
                response.setHeader('Set-Cookie', `jwt=${request.url.split('/').at(-1)}; Path=/; HttpOnly`);
                response.end('{}');
                return;
            }
            if (request.method === 'DELETE') {
                deletionCookies.push(request.headers.cookie ?? '');
                response.statusCode = deletionStatuses.shift() ?? (request.headers.cookie === 'jwt=admin' ? 200 : 403);
                response.end('{}');
                return;
            }
            response.statusCode = 404;
            response.end();
        });
        await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
        baseURL = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
    });

    test.afterEach(async () => {
        await new Promise<void>((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())));
    });

    for (const authorities of [
        ['ROLE_ADMIN', 'ROLE_USER'],
        ['ROLE_SUPER_ADMIN', 'ROLE_USER'],
    ]) {
        test(`accepts an authenticated administrator with ${authorities[0]}`, async ({ playwright }) => {
            account = { login: 'admin', authorities };
            const context = await createAuthenticatedApiContext(playwright.request, baseURL, { username: 'admin', password: 'secret' }, ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN']);
            try {
                await new ExamAPIRequests(context).deleteExam({ id: 42, course: { id: 1 } } as Exam);
                expect(deletionCookies).toEqual(['jwt=admin']);
            } finally {
                await context.dispose();
            }
        });
    }

    test('deletes as admin after the page authenticates as a student', async ({ page, playwright }) => {
        await page.request.post(`${baseURL}/authenticate/admin`);
        const context = await createAuthenticatedApiContext(playwright.request, baseURL, { username: 'admin', password: 'secret' }, ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN']);
        try {
            const adminExamRequests = new ExamAPIRequests(context);
            await page.request.post(`${baseURL}/authenticate/student`);
            expect((await page.context().cookies(baseURL)).find((cookie) => cookie.name === 'jwt')?.value).toBe('student');

            await adminExamRequests.deleteExam({ id: 42, course: { id: 1 } } as Exam);
            expect(deletionCookies).toEqual(['jwt=admin']);
        } finally {
            await context.dispose();
        }
    });

    test('retries a transient server failure without changing its admin identity', async ({ playwright }) => {
        deletionStatuses = [500, 200];
        const context = await createAuthenticatedApiContext(playwright.request, baseURL, { username: 'admin', password: 'secret' }, ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN']);
        try {
            await new ExamAPIRequests(context).deleteExam({ id: 42, course: { id: 1 } } as Exam);
            expect(deletionCookies).toEqual(['jwt=admin', 'jwt=admin']);
        } finally {
            await context.dispose();
        }
    });

    test('fails cleanup immediately when its API identity is forbidden', async ({ playwright }) => {
        const context = await playwright.request.newContext({ baseURL });
        try {
            await context.post('/authenticate/student');
            await expect(new ExamAPIRequests(context).deleteExam({ id: 42, course: { id: 1 } } as Exam)).rejects.toThrow('Failed to delete exam 42: 403');
            expect(deletionCookies).toEqual(['jwt=student']);
        } finally {
            await context.dispose();
        }
    });

    for (const invalidAccount of [
        { login: 'student', authorities: ['ROLE_ADMIN'] },
        { login: 'admin', authorities: ['ROLE_USER'] },
        { login: 'student', authorities: ['ROLE_USER'] },
    ]) {
        test(`rejects and disposes an unexpected identity: ${invalidAccount.login}/${invalidAccount.authorities[0]}`, async ({ playwright }) => {
            account = invalidAccount;
            let context: APIRequestContext;
            const factory = {
                newContext: async (options) => {
                    context = await playwright.request.newContext(options);
                    return context;
                },
            } as APIRequest;
            await expect(createAuthenticatedApiContext(factory, baseURL, { username: 'admin', password: 'secret' }, ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN'])).rejects.toThrow(
                'API identity mismatch',
            );
            await expect(context!.get('/api/core/public/account')).rejects.toThrow(/context or browser has been closed/);
        });
    }

    test('surfaces rejected authentication and disposes its context', async ({ playwright }) => {
        authenticationStatus = 401;
        let context: APIRequestContext;
        const factory = {
            newContext: async (options) => {
                context = await playwright.request.newContext(options);
                return context;
            },
        } as APIRequest;
        await expect(createAuthenticatedApiContext(factory, baseURL, { username: 'admin', password: 'secret' }, ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN'])).rejects.toThrow(
            'API authentication failed for admin: 401',
        );
        await expect(context!.get('/api/core/public/account')).rejects.toThrow(/context or browser has been closed/);
    });
});

test('skips programming test visibility readiness when build result checks are disabled', async () => {
    let visibilityChanges = 0;
    const exerciseRequests = {
        changeProgrammingExerciseTestVisibility: async () => {
            visibilityChanges++;
        },
    } as unknown as ExerciseAPIRequests;
    const groups = new ExamExerciseGroupCreationPage({} as Page, {} as ExamAPIRequests, exerciseRequests);
    groups.handleAddGroupWithExercise = async () => ({ id: 42 }) as Exercise;

    await groups.addGroupWithExercise({} as Exam, ExerciseType.PROGRAMMING, { skipBuildResultCheck: true });
    expect(visibilityChanges).toBe(0);

    await groups.addGroupWithExercise({} as Exam, ExerciseType.PROGRAMMING);
    expect(visibilityChanges).toBe(1);
});
