import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { enableIrisForCourse, loginWithoutIrisTour } from '../../support/irisSetup';
import { admin, studentOne } from '../../support/users';
import { Course } from 'app/course/shared/entities/course.model';
import { createCourseHomeFixture, COURSE_HOME_SITUATIONS } from 'app/course/overview/course-home/test/course-home.fixtures';
import { courseUpdateDTOFromManagementDTO } from '../../support/requests/CourseManagementAPIRequests';
import { CourseManagementDTO } from 'app/course/shared/entities/course-management-response.dto';
import { CourseHome } from 'app/course/overview/course-home/course-home.model';

test.describe('Student course Home', { tag: '@fast' }, () => {
    let course: Course;
    test.beforeEach(async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ courseName: 'Software Engineering — Course Home' });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
    });
    test.afterEach(async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
    test('opens Home on first entry, acknowledges the rendered visit and links to existing exercises', async ({ page, login, exerciseAPIRequests }) => {
        const exercise = await exerciseAPIRequests.createTextExercise({ course });
        const management = await page.request.get(`api/course/courses/${course.id}`);
        const data = courseUpdateDTOFromManagementDTO((await management.json()) as CourseManagementDTO);
        data.studentWelcomeContext = 'Learn by building small examples and reflecting on feedback.';
        const updated = await page.request.put(`api/course/courses/${course.id}`, {
            multipart: { course: { name: 'course', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(data)) } },
        });
        expect(updated.ok()).toBeTruthy();
        await login(studentOne, `/courses/${course.id}`);
        await expect(page).toHaveURL(new RegExp(`/courses/${course.id}/home$`));
        await expect(page.getByTestId('home-welcome')).toBeVisible();
        await expect(page.getByTestId('home-team')).toBeVisible();
        await expect(page.getByText('Learn by building small examples and reflecting on feedback.', { exact: true })).toBeVisible();
        await expect
            .poll(async () => {
                const response = await page.request.get(`api/course/courses/${course.id}/home`);
                return ((await response.json()) as CourseHome).visit.firstVisit;
            })
            .toBe(false);
        await page.getByTestId('home-next-step').getByRole('link', { name: exercise.title! }).click();
        await expect(page).toHaveURL(new RegExp(`/courses/${course.id}/exercises/${exercise.id}`));
        await expect(page.locator('jhi-course-exercise-details')).toBeVisible();
        await page.locator('#home-tab').click();
        await expect(page.getByTestId('home-welcome')).toBeVisible();
    });
    test('opens Home directly after self-enrollment without reloading the account', async ({ page, login, courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
        course = await courseManagementAPIRequests.createCourse({ courseName: 'Self-enrollment into Course Home' });
        const management = await page.request.get(`api/course/courses/${course.id}`);
        const data = courseUpdateDTOFromManagementDTO((await management.json()) as CourseManagementDTO);
        data.enrollmentEnabled = true;
        data.enrollmentStartDate = new Date(Date.now() - 3600000).toISOString();
        data.enrollmentEndDate = new Date(Date.now() + 3600000).toISOString();
        expect(
            (
                await page.request.put(`api/course/courses/${course.id}`, {
                    multipart: { course: { name: 'course', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(data)) } },
                })
            ).ok(),
        ).toBeTruthy();
        const homeRequests: string[] = [];
        page.on('request', (request) => {
            if (request.url().endsWith(`/courses/${course.id}/home`)) homeRequests.push(request.url());
        });
        await login(studentOne, `/courses/${course.id}/register`);
        const enroll = page.getByRole('button', { name: 'Enroll', exact: true });
        await expect(enroll).toBeVisible();
        expect(homeRequests).toEqual([]);
        await enroll.click();
        await expect(page).toHaveURL(new RegExp(`/courses/${course.id}/home$`));
        await expect(page.getByTestId('home-welcome')).toBeVisible();
    });

    test('opens the original question at its unread reply and refreshes its read state', async ({ page, login, communicationAPIRequests }) => {
        await login(studentOne);
        await communicationAPIRequests.acceptCodeOfConduct(course);
        const channel = (await communicationAPIRequests.getCourseWideChannels(course.id!)).find((entry) => !entry.isAnnouncementChannel)!;
        const question = await communicationAPIRequests.createCoursePost(course, 'How can I explain coupling with a concrete example?', channel);
        await communicationAPIRequests.markConversationAsRead(course.id!, channel.id!);
        await login(admin);
        const reply = await communicationAPIRequests.createCourseMessageReply(course, question, 'Try comparing two modules that share implementation details.');
        await login(studentOne, `/courses/${course.id}/home`);
        const questions = page.getByTestId('home-questions');
        await expect(questions).toContainText('1 unread replies');
        const questionLink = questions.getByRole('link', { name: /How can I explain coupling/ });
        await expect(questionLink).toHaveAttribute('href', new RegExp(`conversationId=${channel.id}.*messageId=${question.id}.*focusReplyId=${reply.id}`));
        await questionLink.click();
        // Communication consumes the one-shot focus parameter while opening the thread.
        await expect(page).toHaveURL(new RegExp(`conversationId=${channel.id}.*messageId=${question.id}`));
        await expect(page.locator(`#item-${reply.id}`)).toContainText('Try comparing two modules that share implementation details.');
        await expect(page.locator(`#item-${reply.id}`)).toBeVisible();
        await page.locator('#home-tab').click();
        await expect(page.getByTestId('home-questions')).not.toContainText('unread replies');
    });
    test('keeps a course with communication and Iris disabled usable', async ({ page, login, courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
        course = await courseManagementAPIRequests.createCourse({ allowCommunication: false, allowMessaging: false });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await login(studentOne, `/courses/${course.id}`);
        await expect(page.getByTestId('home-welcome')).toBeVisible();
        await expect(page.getByTestId('home-questions')).toHaveCount(0);
        await expect(page.getByTestId('home-iris')).toHaveCount(0);
        await expect(page.getByRole('link', { name: 'All exercises', exact: true })).toBeVisible();
    });
    test('keeps deterministic content and resource links usable during a welcome timeout', async ({ page, login }) => {
        await login(studentOne);
        expect((await page.request.put('api/account/users/select-llm-usage', { data: { selection: 'CLOUD_AI' } })).ok()).toBeTruthy();
        let welcomeRequested = false;
        await page.route(`**/api/course/courses/${course.id}/home`, async (route) => {
            const response = await route.fetch();
            const home: CourseHome = await response.json();
            home.welcome.aiEligible = true;
            await route.fulfill({ response, json: home });
        });
        await page.route('**/api/course/courses/*/home/welcome', () => {
            welcomeRequested = true;
            return new Promise(() => undefined);
        });
        await login(studentOne, `/courses/${course.id}/home`);
        await expect(page.getByTestId('home-welcome-body')).not.toBeEmpty();
        await expect.poll(() => welcomeRequested).toBe(true);
        await expect.poll(async () => ((await (await page.request.get(`api/course/courses/${course.id}/home`)).json()) as CourseHome).visit.firstVisit).toBe(false);
        await expect(page.getByRole('link', { name: 'All exercises', exact: true })).toBeVisible();
        await expect(page.getByText('AI-generated encouragement', { exact: true })).toHaveCount(0);
    });
    test('uses the existing AI preferences for a standalone local Home provider', async ({ page, login }) => {
        test.skip(process.env.COURSE_HOME_PROVIDER_SMOKE !== 'true', 'Requires an explicitly configured local test provider.');
        const info = await (await page.request.get('management/info')).json();
        expect(info.courseHomeAiAvailable).toBe(true);
        expect(info.courseHomeAiLocation).toBe('LOCAL');
        expect(info.activeModuleFeatures).not.toContain('iris');
        await login(studentOne, '/user-settings/ai-experience');
        await expect(page).toHaveURL(/user-settings\/ai-experience$/);
        await expect(page.getByRole('heading', { name: 'Data Management', exact: true })).toHaveCount(0);
        await page.getByRole('button', { name: /Change Selection|Choose Now/i }).click();
        const saved = page.waitForResponse((response) => response.url().endsWith('/api/account/users/select-llm-usage') && response.request().method() === 'PUT');
        await page
            .getByTestId('llm-selection-modal')
            .getByRole('button', { name: /On-premise/i })
            .click();
        expect((await saved).ok()).toBeTruthy();
        const generated = page.waitForResponse((response) => response.url().endsWith(`/courses/${course.id}/home/welcome`));
        await page.goto(`/courses/${course.id}/home`);
        expect((await generated).ok()).toBeTruthy();
        await expect(page.getByText('AI-generated encouragement', { exact: true })).toBeVisible();
        await expect(page.getByTestId('home-welcome-body')).not.toBeEmpty();
        expect((await page.request.put('api/account/users/select-llm-usage', { data: { selection: 'NO_AI' } })).ok()).toBeTruthy();
        await page.reload();
        await expect(page.getByTestId('home-welcome')).toBeVisible();
        await expect(page.getByText('AI-generated encouragement', { exact: true })).toHaveCount(0);
        expect(((await (await page.request.get(`api/course/courses/${course.id}/home`)).json()) as CourseHome).welcome.aiEligible).toBe(false);
    });
    test('resumes the selected existing Iris session without creating or sending anything', async ({ page, login }) => {
        const info = await (await page.request.get('management/info')).json();
        test.skip(!info.activeModuleFeatures?.includes('iris'), 'Requires the optional Iris module');
        await enableIrisForCourse(page.request, course.id!);
        await login(studentOne);
        expect((await page.request.put('api/account/users/select-llm-usage', { data: { selection: 'CLOUD_AI' } })).ok()).toBeTruthy();
        const created = await page.request.post(`api/iris/chat/sessions?courseId=${course.id}`);
        expect(created.ok()).toBeTruthy();
        const session = await created.json();
        const message = 'Help me reason about coupling and cohesion.';
        // Generation may be unavailable; the fixture is verified through the authorized session read.
        await page.request.post(`api/iris/sessions/${session.id}/messages`, { data: { content: [{ type: 'text', textContent: message }], uncommittedFiles: {} } });
        const saved = await page.request.get(`api/iris/chat/courses/${course.id}/sessions/${session.id}`);
        expect(saved.ok()).toBeTruthy();
        expect(JSON.stringify(await saved.json())).toContain(message);
        await loginWithoutIrisTour(page, studentOne, `/courses/${course.id}/home`);
        const link = page.getByTestId('home-iris').locator(`a[href="/courses/${course.id}/iris?irisSession=${session.id}"]`);
        await expect(link).toBeVisible();
        const mutations: string[] = [];
        page.on('request', (request) => {
            if (request.method() === 'POST' && /api\/iris\/(?:chat\/sessions|sessions\/\d+\/messages)/.test(request.url())) mutations.push(request.url());
        });
        await link.click();
        await expect(page).toHaveURL(new RegExp(`irisSession=${session.id}$`));
        await expect(page.getByText(message, { exact: true })).toBeVisible();
        expect(mutations).toEqual([]);
    });

    test('renders the approved sixteen-situation visual matrix using the shipped page', async ({ page, login }, testInfo) => {
        let snapshot = createCourseHomeFixture('first');
        await page.route(`**/api/course/courses/${course.id}/home`, (route) => route.fulfill({ json: snapshot }));
        await page.route(`**/api/course/courses/${course.id}/home/visits`, (route) => route.fulfill({ status: 204 }));
        await login(studentOne, `/courses/${course.id}/home`);
        for (const situation of COURSE_HOME_SITUATIONS) {
            snapshot = createCourseHomeFixture(situation);
            snapshot.course.id = course.id!;
            snapshot.welcome.aiEligible = false;
            await page.goto(`/courses/${course.id}/home`);
            await expect(page.getByTestId('home-welcome')).toBeVisible();
            await expect(page.getByTestId('course-home')).toHaveAttribute('aria-busy', 'false');
            const overflow = await page.getByTestId('course-home').evaluate((element) => element.scrollWidth > element.clientWidth + 1);
            expect(overflow, `${situation} must fit the content area`).toBe(false);
            expect(await page.locator('#home-updates-heading').evaluate((element) => parseFloat(getComputedStyle(element).fontSize))).toBeLessThanOrEqual(20);
            await page.getByTestId('course-home').screenshot({ path: testInfo.outputPath(`${situation}.png`) });
        }
    });

    test('renders loading, retry and isolated section failures', async ({ page, login }, testInfo) => {
        const snapshot = createCourseHomeFixture('first');
        snapshot.course.id = course.id!;
        snapshot.welcome.aiEligible = false;
        snapshot.questions.status = 'UNAVAILABLE';
        snapshot.irisSessions.status = 'UNAVAILABLE';
        snapshot.sections.lectures = 'UNAVAILABLE';
        snapshot.sections.faq = 'UNAVAILABLE';
        let release: (() => Promise<void>) | undefined;
        let failed = true;
        await page.route(`**/api/course/courses/${course.id}/home`, async (route) => {
            if (failed)
                await new Promise<void>((resolve) => {
                    release = () => route.fulfill({ status: 503, json: { title: 'Temporarily unavailable' } }).then(resolve);
                });
            else await route.fulfill({ json: snapshot });
        });
        await page.route(`**/api/course/courses/${course.id}/home/visits`, (route) => route.fulfill({ status: 204 }));
        await login(studentOne, `/courses/${course.id}/home`);
        await expect(page.getByTestId('course-home')).toHaveAttribute('aria-busy', 'true');
        await page.screenshot({ path: testInfo.outputPath('loading.png'), fullPage: true });
        await release!();
        await expect(page.getByTestId('course-home')).toContainText('could not be loaded');
        await page.screenshot({ path: testInfo.outputPath('retry.png'), fullPage: true });
        failed = false;
        await page.getByRole('button', { name: 'Try again', exact: true }).click();
        await expect(page.getByTestId('home-welcome')).toBeVisible();
        await expect(page.getByTestId('home-questions')).toContainText('temporarily unavailable');
        await page.screenshot({ path: testInfo.outputPath('section-failure.png'), fullPage: true });
    });

    test('renders long German content on narrow and dark surfaces', async ({ page, login }, testInfo) => {
        let snapshot = createCourseHomeFixture('first');
        await page.route(`**/api/course/courses/${course.id}/home`, (route) => route.fulfill({ json: snapshot }));
        await page.route(`**/api/course/courses/${course.id}/home/visits`, (route) => route.fulfill({ status: 204 }));
        await login(studentOne);
        snapshot = createCourseHomeFixture('first');
        snapshot.course.id = course.id!;
        snapshot.welcome.aiEligible = false;
        snapshot.course.title = 'Softwareentwicklungspraktikum für verteilte und nebenläufige Anwendungssysteme';
        snapshot.teachingTeam.items![0].name = 'Alexandra von der Softwareentwicklungsforschung';
        await page.route('**/api/core/public/account', async (route) => {
            const response = await route.fetch();
            await route.fulfill({ response, json: { ...(await response.json()), langKey: 'de' } });
        });
        await page.goto(`/courses/${course.id}/home`);
        await expect(page.getByTestId('home-welcome')).toBeVisible();
        await expect(page.getByRole('heading', { name: 'Dein Kursteam', exact: true })).toBeVisible();
        await page.screenshot({ path: testInfo.outputPath('german-desktop.png'), fullPage: true });
        for (const width of [390, 1440]) {
            await page.setViewportSize({ width, height: 1000 });
            for (const theme of ['light', 'dark']) {
                await page.evaluate((value) => localStorage.setItem('artemisapp.theme.preference', JSON.stringify(value === 'dark' ? 'DARK' : 'LIGHT')), theme);
                await page.reload();
                await expect(page.getByTestId('home-welcome')).toBeVisible();
                await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
                await expect(page.getByTestId('home-refresh')).toHaveCSS('padding-top', '6px');
                await page.getByTestId('home-welcome').scrollIntoViewIfNeeded();
                await page.screenshot({ path: testInfo.outputPath(`${width}-${theme}-top.png`) });
                await page.getByTestId('home-team').scrollIntoViewIfNeeded();
                await page.screenshot({ path: testInfo.outputPath(`${width}-${theme}-team.png`) });
            }
        }
    });
});
