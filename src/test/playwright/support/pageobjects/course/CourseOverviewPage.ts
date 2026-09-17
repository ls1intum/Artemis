import { BASE_API } from '../../constants';
import { ElementHandle, Locator, Page, Response, expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the Course Overview page (/courses/*).
 */
export class CourseOverviewPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Searches for exercises using the provided search term.
     * @param term The search term to use.
     */
    async search(term: string) {
        // Matched through the filter component rather than an id: the shared field no longer carries one,
        // since a hardcoded id collides wherever two search filters render on the same page.
        const searchInput = this.page.locator('jhi-sidebar jhi-search-filter input');
        await searchInput.pressSequentially(term, { delay: 20 });
    }

    /**
     * Initiates the start of an exercise given its ID.
     * @param exerciseId The ID of the exercise to start.
     */
    async startExercise(exerciseId: number) {
        // Wait for the start-exercise button to be visible before clicking; the exercise list is
        // populated asynchronously and the bare .click() races the render under parallel load.
        const button = this.getStartExerciseButton(exerciseId);
        await button.waitFor({ state: 'visible', timeout: 30_000 });
        await button.click();
    }

    /**
     * Starts a practice attempt for an ended quiz via the dedicated "Start practice" action button in the exercise
     * header. Works for both the first attempt and subsequent attempts (the button reappears after each submit).
     * @param exerciseId The id of the quiz exercise to practice.
     */
    async startQuizPractice(exerciseId: number) {
        const button = this.page.locator(`#quiz-start-practice-${exerciseId}`);
        await button.waitFor({ state: 'visible', timeout: 30_000 });

        // Starting a practice attempt loads the quiz for the student, both for the first attempt and for a restart in
        // the same session, and then renders its first question. A load alone does not prove the attempt started: the
        // exercise page loads the same quiz on its own, so a click the header's re-render swallowed would pass too,
        // and the caller is then left waiting for a question that no attempt is behind. What separates them is a load
        // whose request *started* after the click, and the boundary has to come from the click event itself - a
        // timestamp taken before it also covers the click's own actionability wait, which is long enough for the
        // page's automatic load to begin inside it.
        const question = this.page.locator('#question0');
        // Every quiz load is recorded with the time its request started, and the decision is taken once the click time
        // is known. Deciding inside the response predicate instead would race the click time being read back and
        // discard the very load the click caused.
        const loadStartTimes: number[] = [];
        const recordQuizLoad = (response: Response) => {
            if (!response.url().includes(`/quiz-exercises/${exerciseId}/for-student`) || !response.ok()) {
                return;
            }
            try {
                loadStartTimes.push(response.request().timing().startTime);
            } catch {
                // No timing for this response, so it cannot be attributed to the click; ignore it.
            }
        };
        this.page.on('response', recordQuizLoad);
        try {
            for (let attempt = 0; attempt < 3; attempt++) {
                // The question of a previous attempt, if one is on screen. It has to be gone before a visible question
                // counts as this attempt's: the response is reported when its headers arrive, before Angular has
                // applied the body, so the submitted attempt's question would otherwise pass as the new one and the
                // caller's next answer click would land on the controls of the attempt that is already over.
                const previousQuestion = await question.elementHandle({ timeout: 1_000 }).catch(() => null);
                await this.recordNextClickTime(`#quiz-start-practice-${exerciseId}`);
                // The click is part of what is retried: the same re-render that swallows a click also detaches the
                // button, and letting that error escape would end the helper instead of trying again.
                await button.click({ timeout: 10_000 }).catch(() => undefined);
                const clickedAt = await this.readRecordedClickTime();
                const loadedByThisClick = await this.waitUntil(() => loadStartTimes.some((startTime) => startTime >= clickedAt), 15_000);
                if (loadedByThisClick && (await this.hasFreshQuestion(previousQuestion, question))) {
                    return;
                }
                await previousQuestion?.dispose().catch(() => undefined);
                await button.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => undefined);
            }
        } finally {
            this.page.off('response', recordQuizLoad);
            await this.stopRecordingClickTime();
        }
        throw new Error(`Could not start a practice attempt for quiz ${exerciseId}: no attempt loaded a question after clicking start practice.`);
    }

    /**
     * Whether a question belonging to the new attempt is on screen: the previous attempt's question, if there was one,
     * has to be gone first, and a question has to be visible afterwards.
     */
    private async hasFreshQuestion(previousQuestion: ElementHandle<SVGElement | HTMLElement> | null, question: Locator): Promise<boolean> {
        if (previousQuestion) {
            const replaced = await previousQuestion.waitForElementState('hidden', { timeout: 15_000 }).then(
                () => true,
                () => false,
            );
            await previousQuestion.dispose().catch(() => undefined);
            if (!replaced) {
                return false;
            }
        }
        return await question.waitFor({ state: 'visible', timeout: 15_000 }).then(
            () => true,
            () => false,
        );
    }

    /** Polls a condition until it holds or the timeout passes, reporting which happened rather than throwing. */
    private async waitUntil(condition: () => boolean, timeout: number): Promise<boolean> {
        const deadline = Date.now() + timeout;
        while (Date.now() < deadline) {
            if (condition()) {
                return true;
            }
            await this.page.waitForTimeout(200);
        }
        return condition();
    }

    /**
     * Arms a one-shot listener that stores the page's own clock reading of the next click on `selector`.
     * <p>
     * The page clock is what request timings are measured against, and the click event is the only point that is
     * neither before the click's actionability wait nor after the request it triggers.
     */
    private async recordNextClickTime(selector: string) {
        await this.page.evaluate((clickSelector) => {
            const store = window as unknown as { __artemisClickedAt?: number; __artemisClickListener?: (event: Event) => void };
            store.__artemisClickedAt = undefined;
            if (store.__artemisClickListener) {
                document.removeEventListener('click', store.__artemisClickListener, true);
            }
            // Listening on the document rather than on the element resolved right now: the header re-render that this
            // helper exists to survive also replaces the button, and a listener left on the detached node would miss
            // the click that landed on its replacement, so a successful attempt would look like a swallowed one.
            store.__artemisClickListener = (event: Event) => {
                if ((event.target as Element | null)?.closest(clickSelector)) {
                    store.__artemisClickedAt = Date.now();
                }
            };
            document.addEventListener('click', store.__artemisClickListener, true);
        }, selector);
    }

    /** Removes the click listener again, so it cannot outlive the helper and record a later, unrelated click. */
    private async stopRecordingClickTime() {
        await this.page
            .evaluate(() => {
                const store = window as unknown as { __artemisClickListener?: (event: Event) => void };
                if (store.__artemisClickListener) {
                    document.removeEventListener('click', store.__artemisClickListener, true);
                    store.__artemisClickListener = undefined;
                }
            })
            .catch(() => undefined);
    }

    /** The recorded click time, or infinity when no click reached the element, so nothing counts as caused by it. */
    private async readRecordedClickTime(): Promise<number> {
        return await this.page.evaluate(() => (window as unknown as { __artemisClickedAt?: number }).__artemisClickedAt ?? Number.POSITIVE_INFINITY);
    }

    /**
     * Retrieves the Locator for an exercise card by its ID.
     * @param exerciseName title of the exercise.
     * @returns The Locator for the exercise card.
     */
    getExercise(exerciseName: string): Locator {
        return this.page.locator('#test-sidebar-card-medium').getByText(exerciseName);
    }

    /**
     * Retrieves the Locator for all exercises.
     * @returns The Locator for all exercises.
     */
    getExercises(): Locator {
        return this.page.locator('#test-sidebar-card-medium');
    }

    /**
     * Retrieves the Locator for the button opening running exercise with the given ID.
     * @param exerciseId The ID of the exercise.
     * @returns The Locator for the button opening running exercise.
     */
    getOpenRunningExerciseButton(exerciseId: number) {
        return this.page.locator(`#open-exercise-${exerciseId}`);
    }
    /**
     * Retrieves the Locator for the start exercise button by its ID.
     * @param exerciseId The ID of the exercise.
     * @returns The Locator for the start exercise button.
     */
    getStartExerciseButton(exerciseId: number) {
        return this.page.locator(`#start-exercise-${exerciseId}`);
    }

    /**
     * Enters an exercise participation regardless of whether it still has to be started.
     *
     * For a TEAM exercise the participation belongs to the whole team, so as soon as one member starts
     * it, every other member's open page is pushed a participation update over the websocket and
     * navigated straight into the participation (the editor) — where neither "Start exercise" nor
     * "Open exercise" exists any more. A second member calling {@link startExercise} therefore waits
     * for a button that is already gone and times out. The failure was timing-dependent, because it
     * only happened once the update had arrived.
     *
     * This helper is defined by its outcome instead: end up in the participation view. If the page is
     * already there, there is nothing to click; otherwise click whichever button is present, tolerating
     * a lost race against that navigation.
     *
     * @param exerciseId The ID of the exercise to enter.
     */
    async startOrOpenExercise(exerciseId: number) {
        if (this.isInParticipationView()) {
            return;
        }
        const button = this.getStartExerciseButton(exerciseId).or(this.getOpenRunningExerciseButton(exerciseId)).first();
        try {
            await button.click({ timeout: 30_000 });
        } catch (error) {
            // The click races the websocket participation update: if it lands first this page has
            // already navigated into the editor and both buttons are gone, so the click fails on a
            // detached/absent element. Being in the participation view is the outcome we wanted.
            if (!this.isInParticipationView()) {
                throw error;
            }
        }
    }

    /** True once this page shows an exercise participation (the editor), rather than the exercise details. */
    private isInParticipationView(): boolean {
        return this.page.url().includes('/participate/');
    }

    /**
     * Clicks the start practice button for an exercise given its ID.
     * @param exerciseId The ID of the exercise to start in practice mode.
     */
    async startPracticeExercise(exerciseId: number) {
        await this.page.locator(`#start-practice-${exerciseId} button`).click();
    }

    /**
     * Verifies that the result badge for the given exercise renders with the expected score in the course-overview
     * sidebar card. This exercises the `isInSidebarCard` placement of {@code jhi-result} (rendered via
     * {@code jhi-updating-result}, non-clickable, no completion timestamp), which the code-editor / exercise-header
     * placements do not cover. Call this after the build/assessment has completed; the for-dashboard data can briefly
     * lag a just-finished build, so this re-navigates (full page.goto) up to six times to re-fetch, mirroring
     * {@code ProgrammingExerciseOverviewPage.checkResultScoreAfterBuild}.
     * @param courseId The id of the course to open.
     * @param exerciseTitle The title of the exercise whose sidebar card to check.
     * @param expectedResult The expected result score text (or pattern) shown in the badge.
     */
    async checkExerciseResultInSidebar(courseId: number, exerciseTitle: string, expectedResult: string | RegExp) {
        const sidebarResult = () => this.page.locator('#test-sidebar-card-medium', { hasText: exerciseTitle }).first().locator('#result-score');
        for (let attempt = 0; attempt < 6; attempt++) {
            await this.page.goto(`/courses/${courseId}/exercises`);
            await this.page.waitForLoadState('domcontentloaded');
            try {
                await expect(sidebarResult()).toContainText(expectedResult, { timeout: 15000 });
                return;
            } catch {
                // The course-overview dashboard data has not refreshed with the new result yet; re-navigate to re-fetch.
            }
        }
        await this.page.goto(`/courses/${courseId}/exercises`);
        await this.page.waitForLoadState('domcontentloaded');
        await expect(sidebarResult()).toContainText(expectedResult, { timeout: 30000 });
    }

    /**
     * Returns the sidebar card element for the given exercise (the `#test-sidebar-card-medium` entry whose text
     * contains the title). Useful for asserting live, websocket-driven state transitions of its result badge
     * without re-navigating.
     * @param exerciseTitle The title of the exercise whose sidebar card to locate.
     */
    getExerciseSidebarCard(exerciseTitle: string): Locator {
        return this.page.locator('#test-sidebar-card-medium', { hasText: exerciseTitle }).first();
    }

    /**
     * Opens an exercise by id and waits for its detail page.
     * <p>
     * Preferred over {@link openExercise} whenever the caller knows the id and the point of the test lies on the
     * exercise page rather than in the sidebar: the sidebar list re-renders as the exercise data, the grouping and
     * the page's auto-navigation settle, so its card detaches under a click that has already passed the actionability
     * check. Addressing the exercise directly takes that churn out of the test.
     *
     * @param courseId The id of the course the exercise belongs to.
     * @param exerciseId The id of the exercise to open.
     */
    async openExerciseById(courseId: number, exerciseId: number) {
        await this.page.goto(`/courses/${courseId}/exercises/${exerciseId}`);
        await this.page.locator('jhi-course-exercise-details').waitFor({ state: 'visible', timeout: 30000 });
    }

    /**
     * Opens an exercise given its name.
     * @param exerciseName The title of the exercise to open.
     */
    async openExercise(exerciseName: string) {
        // Wait for the sidebar entry we are about to click, not for the detail pane. The exercises page only
        // renders `jhi-course-exercise-details` once an exercise is selected, and selection before the click
        // is up to the page's auto-navigation, which picks the upcoming or last-visited exercise and does
        // nothing at all when it finds neither. Waiting for the detail pane first therefore waited for a
        // component that only this click can bring up, and the test hung until its timeout with the exercise
        // list fully rendered in front of it.
        // The click is retried because the sidebar re-renders while the exercise list and the auto-navigation settle,
        // which swallows a click that landed on the outgoing card without ever opening the exercise.
        const card = this.getExercise(exerciseName);
        await card.waitFor({ state: 'visible', timeout: 30000 });
        // What is waited for is the requested exercise's own title in the detail pane, not merely a detail pane: the
        // page may already be showing an auto-selected different exercise, and waiting for the pane alone would accept
        // that one and let a swallowed click pass as success.
        const requestedExerciseIsOpen = this.page.locator('jhi-course-exercise-details').getByText(exerciseName).first();
        for (let attempt = 0; attempt < 3; attempt++) {
            try {
                await card.click({ timeout: 10000 });
                await requestedExerciseIsOpen.waitFor({ state: 'visible', timeout: 15000 });
                return;
            } catch (error) {
                if (attempt === 2) {
                    throw error;
                }
            }
        }
    }

    /**
     * Opens a running programming exercise and waits for the necessary request to complete.
     * @param exerciseID The ID of the programming exercise to open.
     */
    async openRunningProgrammingExercise(exerciseID: number) {
        const responsePromise = this.page.waitForRequest(`${BASE_API}/programming/programming-exercise-participations/*/student-participation-with-latest-result-and-feedbacks`);
        await responsePromise;
    }

    /**
     * Navigates to the Exams tab on the course overview page.
     */
    async openExamsTab() {
        await this.page.locator('#exam-tab').click();
    }

    /**
     * Opens an exam given its title.
     */
    async openExam(examTitle: string): Promise<void> {
        const examLink = this.page.locator('span').filter({ hasText: examTitle });
        await examLink.waitFor({ state: 'visible', timeout: 30000 });
        await examLink.click();
    }

    /**
     * Opens the team info for the exercise.
     */
    async openTeam() {
        await this.page.locator('.view-team').click();
    }

    /**
     * Verifies that the exercise title is shown in the exercise header.
     * @param exerciseTitle The expected exercise title.
     */
    async shouldShowExerciseTitleInHeader(exerciseTitle: string): Promise<void> {
        await expect(this.page.locator('#exercise-header').getByText(exerciseTitle)).toBeVisible();
    }

    /**
     * Verifies that the problem statement panel is visible.
     */
    async shouldShowProblemStatement(): Promise<void> {
        await expect(this.page.locator('#problem-statement')).toBeVisible();
    }

    /**
     * Clicks the submit button in the shared exercise header and waits for the API response.
     * @param apiPattern The URL pattern of the submission API endpoint to wait for.
     */
    async submitExercise(apiPattern: string) {
        const responsePromise = this.page.waitForResponse(apiPattern);
        await this.page.locator('#submit-exercise, [data-testid="submit-exercise-popover"]').first().click();
        return await responsePromise;
    }
}
