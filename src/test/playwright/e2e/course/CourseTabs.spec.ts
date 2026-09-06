import { Page, expect } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, studentOne, tutor } from '../../support/users';
import { Course } from 'app/course/shared/entities/course.model';
import { generateUUID, titleLowercase } from '../../support/utils';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';

/**
 * One test per course overview tab, each against a course that really has that tab's content, driven as a student.
 *
 * The suite exists because splitting the course overview into per-tab endpoints is only correct if every tab still
 * loads what it renders. The earlier coverage used courses with no content, which hid three real defects: the lectures
 * tab auto-selects a lecture and then no longer recognised a re-selection, the communication tab refreshed itself in an
 * unbounded loop, and the tutorial groups tab refreshed from the stored course instead of the server. Every test here
 * therefore seeds real content and asserts three things — the tab renders that content, the tab's own endpoint is
 * called on entry, and selecting the already-open tab refreshes it exactly once (the tab acts as a refresh button).
 */

/** Records the requests a tab makes, so a test can assert both that it loads and that it does not loop. */
function recordRequests(page: Page, pattern: RegExp): string[] {
    const seen: string[] = [];
    page.on('request', (request) => {
        if (pattern.test(request.url())) {
            seen.push(request.url());
        }
    });
    return seen;
}

/**
 * Answers the "choose your AI experience" modal, which only appears when Iris is enabled on the server and the student
 * has not made that choice yet. It is not the default configuration, but when it is up its backdrop swallows every
 * click on the page, so the suite answers it instead of timing out behind it.
 *
 * @param waitFor how long to give the modal to appear; 0 to only dismiss one that is already up.
 */
async function dismissLlmSelectionIfAsked(page: Page, waitFor: number): Promise<void> {
    const cloudOption = page.locator('jhi-llm-selection-modal .option-card.cloud-card');
    if (waitFor > 0) {
        await cloudOption.waitFor({ state: 'visible', timeout: waitFor }).catch(() => undefined);
    }
    if (await cloudOption.isVisible()) {
        await cloudOption.click();
        await expect(cloudOption).toBeHidden();
    }
}

/** Opens a course tab directly and gets the student to a page they can interact with. */
async function openCourseTab(page: Page, courseId: number, tab: string): Promise<void> {
    await page.goto(`/courses/${courseId}/${tab}`);
    await expect(page.locator('jhi-course-sidebar')).toBeVisible({ timeout: 30_000 });
    await dismissLlmSelectionIfAsked(page, 2000);
}

/**
 * Clicks a sidebar tab. Scoped to the sidebar on purpose: the refresh is driven by the sidebar reporting the click, so
 * a link to the same URL elsewhere on the page navigates without counting as a tab selection.
 */
async function selectTab(page: Page, courseId: number, tab: string): Promise<void> {
    // Safety net for a modal that only came up after the tab had already rendered
    await dismissLlmSelectionIfAsked(page, 0);
    await page.locator(`jhi-course-sidebar a[href="/courses/${courseId}/${tab}"]`).first().click();
}

/** The shared contract of every tab: entering loads it once, and re-selecting it refreshes it exactly once more. */
async function expectLoadsOnceAndRefreshesOnReselect(page: Page, courseId: number, tab: string, requests: string[]): Promise<void> {
    await expect.poll(() => requests.length, { message: `entering ${tab} must load its data`, timeout: 20_000 }).toBeGreaterThan(0);
    // Settle before counting, so a second request that is on its way is counted rather than missed
    await page.waitForTimeout(1500);
    expect(requests, `entering ${tab} must load its data exactly once: ${JSON.stringify(requests)}`).toHaveLength(1);

    await selectTab(page, courseId, tab);
    await expect.poll(() => requests.length, { message: `re-selecting ${tab} must refresh it`, timeout: 20_000 }).toBeGreaterThan(1);
    // The refresh must be a single request, not the first of a loop
    await page.waitForTimeout(1500);
    expect(requests, `re-selecting ${tab} must refresh it exactly once: ${JSON.stringify(requests)}`).toHaveLength(2);
}

/** A sidebar entry of a tab's own inner sidebar (lectures, exams, exercises and tutorial groups all use these cards). */
function sidebarCard(page: Page, title: string) {
    return page.locator('#test-sidebar-card-title', { hasText: title });
}

test.describe('Course overview tabs', { tag: '@fast' }, () => {
    let course: Course;

    test.beforeEach('Create a course the student is enrolled in', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        // Only the tutorial groups test needs the time zone, but setting it here keeps a single shared setup
        course = await courseManagementAPIRequests.createCourse({ timeZone: 'Europe/Berlin' });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
    });

    test.afterEach('Delete the course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test('Exercises tab lists the seeded exercises and never falls back to the dashboard endpoint', async ({ page, login, exerciseAPIRequests, courseOverview }) => {
        // Released but not yet due, so the exercises land in the sidebar group a student sees expanded
        const released = dayjs().subtract(2, 'day');
        const due = dayjs().add(2, 'day');
        const first = await exerciseAPIRequests.createTextExerciseWithDates({ course }, released, due, due.add(1, 'hour'), 'TabEx1 ' + generateUUID());
        const second = await exerciseAPIRequests.createTextExerciseWithDates({ course }, released, due, due.add(1, 'hour'), 'TabEx2 ' + generateUUID());

        await login(studentOne);
        const exerciseRequests = recordRequests(page, new RegExp(`api/course/courses/${course.id}/exercises-for-overview`));
        const dashboardRequests = recordRequests(page, new RegExp(`api/course/courses/${course.id}/for-dashboard`));
        await openCourseTab(page, course.id!, 'exercises');

        await expect(courseOverview.getExercise(first.title!)).toBeVisible();
        await expect(courseOverview.getExercise(second.title!)).toBeVisible();
        expect(dashboardRequests, 'the deprecated whole-course endpoint must not be used').toHaveLength(0);
        await expectLoadsOnceAndRefreshesOnReselect(page, course.id!, 'exercises', exerciseRequests);
        // The refresh must not drop what is on screen — the list is replaced, not emptied and refilled
        await expect(courseOverview.getExercise(first.title!)).toBeVisible();
        await expect(courseOverview.getExercise(second.title!)).toBeVisible();
    });

    test('Lectures tab lists the seeded lectures and refreshes even while a lecture is open', async ({ page, login, courseManagementAPIRequests }) => {
        const first = await courseManagementAPIRequests.createLecture(course, 'TabLec1 ' + generateUUID());
        const second = await courseManagementAPIRequests.createLecture(course, 'TabLec2 ' + generateUUID());

        await login(studentOne);
        const lectureRequests = recordRequests(page, new RegExp(`api/lecture/courses/${course.id}/lectures-for-overview`));
        await openCourseTab(page, course.id!, 'lectures');

        await expect(sidebarCard(page, first.title!)).toBeVisible();
        await expect(sidebarCard(page, second.title!)).toBeVisible();
        // The tab auto-selects a lecture, so by the time the student clicks the tab again the URL is the child's. That
        // is the case the refresh used to miss entirely.
        await expect(page).toHaveURL(new RegExp(`/courses/${course.id}/lectures/\\d+`));
        await expectLoadsOnceAndRefreshesOnReselect(page, course.id!, 'lectures', lectureRequests);
        await expect(sidebarCard(page, first.title!)).toBeVisible();
    });

    test('Exams tab lists an exam the student is registered for', async ({ page, login, examAPIRequests }) => {
        const exam = await examAPIRequests.createExam({
            course,
            title: 'TabExam ' + generateUUID(),
            visibleDate: dayjs().subtract(1, 'hour'),
            startDate: dayjs().add(1, 'day'),
            endDate: dayjs().add(1, 'day').add(1, 'hour'),
        });
        await examAPIRequests.registerStudentForExam(exam, studentOne);

        await login(studentOne);
        const examRequests = recordRequests(page, new RegExp(`api/exam/courses/${course.id}/exams-for-overview`));
        await openCourseTab(page, course.id!, 'exams');

        await expect(sidebarCard(page, exam.title!)).toBeVisible();
        await expectLoadsOnceAndRefreshesOnReselect(page, course.id!, 'exams', examRequests);
        await expect(sidebarCard(page, exam.title!)).toBeVisible();
    });

    test('Statistics tab shows the points of the seeded exercise', async ({ page, login, exerciseAPIRequests }) => {
        // Points only count towards the totals once the exercise is over, so this one is released and already due
        const released = dayjs().subtract(2, 'day');
        const due = dayjs().subtract(1, 'day');
        // The text exercise fixture is worth 10 points and is INCLUDED_COMPLETELY, so it must show up in the totals
        await exerciseAPIRequests.createTextExerciseWithDates({ course }, released, due, due.add(1, 'hour'), 'TabStat ' + generateUUID());

        await login(studentOne);
        const exerciseRequests = recordRequests(page, new RegExp(`api/course/courses/${course.id}/exercises-for-overview`));
        await openCourseTab(page, course.id!, 'statistics');

        // The statistics tab reads the same per-tab payload as the exercises tab; the scores prove it arrived complete
        await expect(page.locator('#max-course-score')).toContainText('10');
        await expect(page.locator('#reachable-course-score')).toContainText('10');
        await expect(page.locator('#absolute-course-score')).toContainText('0');
        await expectLoadsOnceAndRefreshesOnReselect(page, course.id!, 'statistics', exerciseRequests);
        // Refreshing keeps the figures on screen rather than clearing the charts first
        await expect(page.locator('#max-course-score')).toContainText('10');
    });

    test('Communication tab lists a seeded channel and does not refresh itself in a loop', async ({ page, login, communicationAPIRequests }) => {
        const channelName = 'tab-channel-' + titleLowercase(generateUUID());
        const channel = await communicationAPIRequests.createCourseMessageChannel(course, channelName, 'Tab channel', false, true);
        // A public channel is not joined automatically, and the sidebar only lists the conversations the student is in
        await communicationAPIRequests.joinUserIntoChannel(course, channel.id!, studentOne);

        await login(studentOne);
        const conversationRequests = recordRequests(page, new RegExp(`api/communication/courses/${course.id}/conversations(\\?|$)`));
        await openCourseTab(page, course.id!, 'communication');

        // General channels start collapsed, so a student expands the section to reach the channel
        await page.locator('#test-accordion-item-header-generalChannels').click();
        await expect(sidebarCard(page, channelName)).toBeVisible();
        // Entering resolves which conversation to show, which is itself a navigation. Treating that as a re-selection
        // produced hundreds of requests, so the count is asserted instead of merely "more than zero".
        await expect.poll(() => conversationRequests.length, { timeout: 20_000 }).toBeGreaterThan(0);
        await page.waitForTimeout(2000);
        const afterEntry = conversationRequests.length;
        expect(afterEntry, `entering communication must not loop: ${JSON.stringify(conversationRequests)}`).toBeLessThan(6);

        await selectTab(page, course.id!, 'communication');
        await expect.poll(() => conversationRequests.length, { timeout: 20_000 }).toBeGreaterThan(afterEntry);
        await page.waitForTimeout(2000);
        expect(conversationRequests.length, `the refresh must settle: ${JSON.stringify(conversationRequests)}`).toBeLessThan(afterEntry + 4);
        await expect(sidebarCard(page, channelName)).toBeVisible();
    });

    test('Competencies tab lists the seeded competency and prerequisite', async ({ page, login, courseManagementAPIRequests }) => {
        const competencyTitle = 'Tab Competency ' + generateUUID();
        const prerequisiteTitle = 'Tab Prerequisite ' + generateUUID();
        await courseManagementAPIRequests.createCompetency(course, competencyTitle, 'Competency for the tab test');
        await courseManagementAPIRequests.createPrerequisite(course, prerequisiteTitle, 'Prerequisite for the tab test');

        await login(studentOne);
        const competencyRequests = recordRequests(page, new RegExp(`api/atlas/courses/${course.id}/course-competencies`));
        await openCourseTab(page, course.id!, 'competencies');

        await expect(page.getByText(competencyTitle).first()).toBeVisible();
        // Prerequisites live behind their own collapsed section, so the count in its header is what proves they arrived
        const prerequisiteSection = page.locator('.control-label').filter({ hasText: '1' }).first();
        await expect(prerequisiteSection).toBeVisible();
        await prerequisiteSection.click();
        await expect(page.getByText(prerequisiteTitle).first()).toBeVisible();

        await expectLoadsOnceAndRefreshesOnReselect(page, course.id!, 'competencies', competencyRequests);
        await expect(page.getByText(competencyTitle).first()).toBeVisible();
    });

    test('Learning path tab loads the path of the student', async ({ page, login, courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.createCompetency(course, 'Tab LP Competency ' + generateUUID(), 'For the learning path');
        await courseManagementAPIRequests.enableLearningPaths(course);

        await login(studentOne);
        const learningPathRequests = recordRequests(page, new RegExp(`api/atlas/courses/${course.id}/learning-path/me`));
        await openCourseTab(page, course.id!, 'learning-path');

        // The guard must let the tab through instead of bouncing back to the exercises tab
        await expect(page).toHaveURL(new RegExp(`/courses/${course.id}/learning-path`));
        await expectLoadsOnceAndRefreshesOnReselect(page, course.id!, 'learning-path', learningPathRequests);
    });

    test('FAQ tab lists the accepted FAQ and refreshes on re-selection', async ({ page, login, courseManagementAPIRequests }) => {
        const questionTitle = 'Tab FAQ ' + generateUUID();
        await courseManagementAPIRequests.createFaq(course, questionTitle, 'The answer for the tab test.');

        await login(studentOne);
        const faqRequests = recordRequests(page, new RegExp(`api/communication/courses/${course.id}/faq-state`));
        await openCourseTab(page, course.id!, 'faq');

        await expect(page.getByText(questionTitle).first()).toBeVisible();
        await expectLoadsOnceAndRefreshesOnReselect(page, course.id!, 'faq', faqRequests);
        await expect(page.getByText(questionTitle).first()).toBeVisible();
    });

    test('Tutorial groups tab lists the seeded group and refreshes from the server, not the stored course', async ({
        page,
        login,
        courseManagementAPIRequests,
        userManagementAPIRequests,
    }) => {
        await courseManagementAPIRequests.createTutorialGroupsConfiguration(course);
        const tutorUser = await (await userManagementAPIRequests.getUser(tutor.username)).json();
        // The server only accepts alphanumerics, spaces, colons and dashes, at most 20 characters
        const title = 'TabGroup ' + generateUUID().slice(0, 8);
        const tutorialGroupId = await courseManagementAPIRequests.createTutorialGroup(course, title, tutorUser.id);
        // Registering the student puts the group in the expanded "my groups" section, which is where a student looks
        await courseManagementAPIRequests.registerStudentsInTutorialGroup(course, tutorialGroupId, [studentOne]);

        await login(studentOne);
        const groupRequests = recordRequests(page, new RegExp(`api/tutorialgroup/courses/${course.id}/tutorial-groups(\\?|$)`));
        await openCourseTab(page, course.id!, 'tutorial-groups');

        await expect(sidebarCard(page, title)).toBeVisible();
        await expectLoadsOnceAndRefreshesOnReselect(page, course.id!, 'tutorial-groups', groupRequests);
        await expect(sidebarCard(page, title)).toBeVisible();
    });

    test('Calendar tab loads its events and refreshes without changing the month in view', async ({ page, login, courseManagementAPIRequests }) => {
        const lecture = await courseManagementAPIRequests.createLecture(course, 'TabCal ' + generateUUID(), dayjs().add(1, 'hour'), dayjs().add(2, 'hour'));

        await login(studentOne);
        const calendarRequests = recordRequests(page, new RegExp(`api/calendar/courses/${course.id}/calendar-events`));
        await openCourseTab(page, course.id!, 'calendar');

        await expect.poll(() => calendarRequests.length, { timeout: 20_000 }).toBe(1);
        // The month view labels each event cell with its title, which for a lecture is a prefix plus the lecture title
        const lectureEvent = page.locator(`.event-cell[data-testid*="${lecture.title}"]`);
        await expect(lectureEvent.first()).toBeVisible();
        const monthsInView = new URL(calendarRequests[0]).searchParams.getAll('monthKeys');
        expect(monthsInView.length, 'the calendar must request a bounded month range').toBeGreaterThan(0);

        await selectTab(page, course.id!, 'calendar');
        await expect.poll(() => calendarRequests.length, { timeout: 20_000 }).toBe(2);
        expect(new URL(calendarRequests[1]).searchParams.getAll('monthKeys'), 'the refresh must keep the month in view').toEqual(monthsInView);
        await expect(lectureEvent.first()).toBeVisible();
    });

    test('Training tab loads the leaderboard for a course with practice questions', async ({ page, login, exerciseAPIRequests }) => {
        // A quiz whose due date has passed is what makes its questions available for practice, and therefore the tab available
        await exerciseAPIRequests.createQuizExercise({
            body: { course },
            quizQuestions: [multipleChoiceQuizTemplate],
            title: 'TabQuiz ' + generateUUID(),
            releaseDate: dayjs().subtract(2, 'day'),
            dueDate: dayjs().subtract(1, 'day'),
        });

        await login(studentOne);
        const leaderboardRequests = recordRequests(page, new RegExp(`api/quiz/courses/${course.id}/training/leaderboard`));
        await openCourseTab(page, course.id!, 'training');

        // On a student's very first visit the tab asks whether to appear in the leaderboard, behind a modal mask that
        // blocks every other click until it is confirmed. The dialog is appended to the body, not to its host element,
        // and whether it appears depends on whether this student ever answered it, so wait for either outcome.
        const confirmButton = page.getByTestId('quiz-training-confirm-button');
        const leagueBadge = page.locator('jhi-league-badge');
        await expect(confirmButton.or(leagueBadge).first()).toBeVisible({ timeout: 20_000 });
        if (await confirmButton.isVisible()) {
            await confirmButton.click();
        }
        await expect(page.getByTestId('quiz-training-dialog-mask')).toHaveCount(0);
        await expect(leagueBadge).toBeVisible({ timeout: 20_000 });

        await expect.poll(() => leaderboardRequests.length, { timeout: 20_000 }).toBeGreaterThan(0);
        const afterEntry = leaderboardRequests.length;

        await selectTab(page, course.id!, 'training');
        await expect.poll(() => leaderboardRequests.length, { timeout: 20_000 }).toBeGreaterThan(afterEntry);
    });

    test('The sidebar offers exactly the tabs the course has content for', async ({ page, login, courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.createLecture(course, 'TabSide ' + generateUUID());

        await login(studentOne);
        await openCourseTab(page, course.id!, 'exercises');
        const sidebar = page.locator('jhi-course-sidebar');
        await expect(sidebar).toBeVisible();

        // Offered because the course has that content, or because the tab is always offered
        await expect(sidebar.locator(`a[href="/courses/${course.id}/exercises"]`)).toBeVisible();
        await expect(sidebar.locator(`a[href="/courses/${course.id}/lectures"]`)).toBeVisible();
        await expect(sidebar.locator(`a[href="/courses/${course.id}/communication"]`)).toBeVisible();
        await expect(sidebar.locator(`a[href="/courses/${course.id}/statistics"]`)).toBeVisible();
        await expect(sidebar.locator(`a[href="/courses/${course.id}/calendar"]`)).toBeVisible();
        await expect(sidebar.locator(`a[href="/courses/${course.id}/settings"]`)).toBeVisible();

        // Withheld because the course has no such content — the availability endpoint decides this, not the client
        await expect(sidebar.locator(`a[href="/courses/${course.id}/exams"]`)).toHaveCount(0);
        await expect(sidebar.locator(`a[href="/courses/${course.id}/competencies"]`)).toHaveCount(0);
        await expect(sidebar.locator(`a[href="/courses/${course.id}/learning-path"]`)).toHaveCount(0);
        await expect(sidebar.locator(`a[href="/courses/${course.id}/faq"]`)).toHaveCount(0);
        await expect(sidebar.locator(`a[href="/courses/${course.id}/tutorial-groups"]`)).toHaveCount(0);
        await expect(sidebar.locator(`a[href="/courses/${course.id}/training"]`)).toHaveCount(0);

        // The settings entry is not a content tab and loads nothing from the course, but it still has to open
        await selectTab(page, course.id!, 'settings');
        await expect(page.locator('jhi-course-settings')).toBeVisible();
    });
});

/**
 * Regression coverage for a 500 on the exercises tab.
 *
 * A participation with no submission row at all is what broke it: the row projection reaches its submission columns
 * through an outer join, and reading the submission's concrete type off that null row made Hibernate fail to map a null
 * discriminator. Only programming exercises produce that state through the normal flow — starting a text, modeling,
 * quiz or file upload exercise creates an initial empty submission along with the participation, so those never hit it.
 *
 * Separate from the suite above because creating a programming exercise needs repository infrastructure and is far too
 * slow for the `@fast` budget.
 */
test.describe('Course overview tabs with a started programming exercise', { tag: '@slow' }, () => {
    let course: Course;

    test.beforeEach('Create a course with a programming exercise', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
    });

    test.afterEach('Delete the course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test('Exercises tab loads when a programming exercise was started and never submitted', async ({ page, login, exerciseAPIRequests, courseOverview }) => {
        await login(admin);
        const exercise = await exerciseAPIRequests.createProgrammingExercise({ course, releaseDate: dayjs().subtract(2, 'day'), dueDate: dayjs().add(2, 'day') });

        await login(studentOne);
        // Starting a programming exercise creates the participation and its repository, but no submission: the student
        // has not pushed yet. That is the row shape the projection used to fail on.
        const started = await page.request.post(`api/exercise/exercises/${exercise.id}/participations`);
        expect(started.status(), 'the participation must be created for this test to mean anything').toBe(201);

        const failedRequests: string[] = [];
        page.on('response', (response) => {
            if (response.url().includes(`courses/${course.id}/exercises-for-overview`) && !response.ok()) {
                failedRequests.push(`${response.status()} ${response.url()}`);
            }
        });

        await openCourseTab(page, course.id!, 'exercises');

        await expect(courseOverview.getExercise(exercise.title!)).toBeVisible();
        expect(failedRequests, `the exercises tab must not fail: ${JSON.stringify(failedRequests)}`).toHaveLength(0);
    });
});
