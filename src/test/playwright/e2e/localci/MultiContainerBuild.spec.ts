import { Page, expect } from '@playwright/test';

import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import cAllSuccessful from '../../fixtures/exercise/programming/c/all_successful/submission.json';
import { ProgrammingLanguage } from '../../support/constants';
import { test } from '../../support/fixtures';
import { ExerciseAPIRequests } from '../../support/requests/ExerciseAPIRequests';
import { SEED_COURSES } from '../../support/seedData';
import { BUILD_FINISH_TIMEOUT } from '../../support/timeouts';
import { admin, studentOne } from '../../support/users';

const course = { id: SEED_COURSES.programmingParticipation.id } as Course;

/** The test cases the two containers report together; each container reports its own one and the shared one. */
const PRESENT = 'testRepositoryPresent';
const ABSENT = 'testRepositoryAbsent';
const SHARED = 'sharedCheck';

/**
 * A phase that writes a JUnit report with one test case that checks whether the test repository was checked out into
 * the container, and the shared test case, which passes or fails as given. The test repository of a C exercise is
 * checked out into `tests`.
 */
const reportingPhase = (report: string, ownTestCase: string, testRepositoryExpected: boolean, sharedPasses: boolean) => {
    const testCase = (name: string, failureMessage?: string) =>
        `<testcase name="${name}" classname="MultiContainer">${failureMessage ? `<failure message="${failureMessage}"/>` : ''}</testcase>`;
    const presenceCheck = testRepositoryExpected ? '[ -d tests ]' : '[ ! -d tests ]';
    const ownPassed = testCase(ownTestCase);
    const ownFailed = testCase(ownTestCase, testRepositoryExpected ? 'the test repository is missing' : 'the test repository was checked out');
    const shared = sharedPasses ? testCase(SHARED) : testCase(SHARED, 'fails in this container');
    // the XML is single-quoted for the shell, so it contains no single quote of its own
    return {
        name: 'report',
        script: [
            'mkdir -p results',
            `if ${presenceCheck}; then OWN='${ownPassed}'; else OWN='${ownFailed}'; fi`,
            `printf '%s' '<?xml version="1.0" encoding="UTF-8"?><testsuite name="${report}">' "$OWN" '${shared}</testsuite>' > results/TEST-${report}.xml`,
        ].join('\n'),
        condition: 'ALWAYS',
        forceRun: false,
        resultPaths: ['results/*.xml'],
    };
};

/**
 * Two containers in the default image of the language. `instructor_tests` checks out every repository of the exercise;
 * `student_tests` selects none, so it receives only the assignment repository. Both report the shared test case, which
 * fails in `student_tests` only.
 */
const mergingPlan = {
    containers: [
        { name: 'instructor_tests', phases: [reportingPhase('instructor', PRESENT, true, true)] },
        { name: 'student_tests', repositories: [], phases: [reportingPhase('student', ABSENT, false, false)] },
    ],
};

interface TestCase {
    id: number;
    testName: string;
    active: boolean;
}

interface Feedback {
    positive?: boolean;
    // a student receives the test case of an automatic feedback by its id only, as the client resolves the names
    testCase?: { id: number };
}

interface Result {
    id: number;
    completionDate?: string;
    successful?: boolean;
    score?: number;
    feedbacks?: Feedback[];
}

interface ParticipationWithResults {
    submissions?: { buildFailed?: boolean; results?: Result[] }[];
}

interface BuildLogEntry {
    log: string;
    containerName?: string;
}

/** The name of the container that runs into its timeout; no line of its log contains it. */
const TIMED_OUT_CONTAINER = 'slow_container';
const PASSING = 'passingCheck';

/**
 * One container reports a passing test case, the other one sleeps beyond its own timeout, which is shorter than the
 * timeout of the exercise.
 */
const timeoutPlan = {
    containers: [
        {
            name: 'quick_check',
            phases: [
                {
                    name: 'report',
                    script: [
                        'mkdir -p results',
                        `printf '%s' '<?xml version="1.0" encoding="UTF-8"?><testsuite name="quick"><testcase name="${PASSING}" classname="MultiContainer"></testcase></testsuite>' > results/TEST-quick.xml`,
                    ].join('\n'),
                    condition: 'ALWAYS',
                    forceRun: false,
                    resultPaths: ['results/*.xml'],
                },
            ],
        },
        {
            name: TIMED_OUT_CONTAINER,
            timeoutSeconds: 15,
            phases: [{ name: 'wait', script: 'echo "waiting for the timeout"\nsleep 120', condition: 'ALWAYS', forceRun: false, resultPaths: [] }],
        },
    ],
};

test.describe('Multi-container build', { tag: '@slow' }, () => {
    let exercise: ProgrammingExercise | undefined;

    const createExercise = async (login: (user: typeof admin) => Promise<void>, exerciseAPIRequests: ExerciseAPIRequests, buildPlan: object) => {
        await login(admin);
        exercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            programmingLanguage: ProgrammingLanguage.C,
            buildPlanConfiguration: JSON.stringify(buildPlan),
        });
        return exercise;
    };

    /** Waits until the solution build registered exactly these active test cases, and returns their names by id. */
    const expectActiveTestCases = async (page: Page, exerciseId: number, testNames: string[]) => {
        let activeTestCases: TestCase[] = [];
        await expect
            .poll(
                async () => {
                    const response = await page.request.get(`api/programming/programming-exercises/${exerciseId}/test-cases`);
                    if (!response.ok()) {
                        return [];
                    }
                    activeTestCases = ((await response.json()) as TestCase[]).filter((testCase) => testCase.active);
                    return activeTestCases.map((testCase) => testCase.testName).sort();
                },
                { timeout: BUILD_FINISH_TIMEOUT, intervals: [2_000, 5_000] },
            )
            .toEqual([...testNames].sort());
        return new Map(activeTestCases.map((testCase) => [testCase.id, testCase.testName]));
    };

    /**
     * Waits until the submission of the participation has a finished result. The result of a multi-container build exists
     * while it is in progress, so a new result id alone does not mean that every container has reported.
     */
    const waitForFinishedResult = async (page: Page, participationId: number) => {
        let submission: NonNullable<ParticipationWithResults['submissions']>[number] | undefined;
        let result: Result | undefined;
        await expect
            .poll(
                async () => {
                    const response = await page.request.get(
                        `api/programming/programming-exercise-participations/${participationId}/student-participation-with-latest-result-and-feedbacks`,
                    );
                    if (!response.ok()) {
                        return undefined;
                    }
                    submission = ((await response.json()) as ParticipationWithResults).submissions?.[0];
                    result = submission?.results?.find((candidate) => candidate.completionDate);
                    return result?.id;
                },
                { timeout: BUILD_FINISH_TIMEOUT, intervals: [2_000, 5_000] },
            )
            .toBeDefined();
        return { submission: submission!, result: result! };
    };

    const outcomesOf = (result: Result, testNames: Map<number, string>) =>
        Object.fromEntries(
            (result.feedbacks ?? [])
                .filter((feedback) => feedback.testCase)
                .map((feedback) => [testNames.get(feedback.testCase!.id) ?? `unknown test case ${feedback.testCase!.id}`, feedback.positive]),
        );

    test.afterEach('Delete the programming exercise', async ({ login, exerciseAPIRequests }) => {
        if (exercise?.id !== undefined) {
            await login(admin);
            await exerciseAPIRequests.deleteProgrammingExercise(exercise.id);
        }
        exercise = undefined;
    });

    test('Merges the results of every container into one result', async ({ login, page, exerciseAPIRequests }) => {
        // the template and solution builds and the student's build each run two containers
        test.setTimeout(3 * BUILD_FINISH_TIMEOUT);
        const { id: exerciseId } = await createExercise(login, exerciseAPIRequests, mergingPlan);

        // the solution build registers the test cases of both containers, the shared one once
        const testNames = await expectActiveTestCases(page, exerciseId!, [SHARED, ABSENT, PRESENT]);

        await login(studentOne);
        const participation = await (await exerciseAPIRequests.startExerciseParticipation(exerciseId!)).json();
        await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id, cAllSuccessful);
        const { submission, result } = await waitForFinishedResult(page, participation.id);

        // one result for the submission, not one per container
        expect(submission.results).toHaveLength(1);
        // each container received only the repositories it selected
        expect(outcomesOf(result, testNames)).toEqual({ [PRESENT]: true, [ABSENT]: true, [SHARED]: false });
        // the shared test case counts once, as failed, so two of the three test cases pass
        expect(result.score).toBeCloseTo(66.7, 0);
        expect(result.successful).toBe(false);
    });

    test('Fails the build when a container runs into its own timeout', async ({ login, page, exerciseAPIRequests }) => {
        test.setTimeout(3 * BUILD_FINISH_TIMEOUT);
        const { id: exerciseId } = await createExercise(login, exerciseAPIRequests, timeoutPlan);
        const testNames = await expectActiveTestCases(page, exerciseId!, [PASSING]);

        await login(studentOne);
        const participation = await (await exerciseAPIRequests.startExerciseParticipation(exerciseId!)).json();
        await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id, cAllSuccessful);
        const { submission, result } = await waitForFinishedResult(page, participation.id);

        // the build still finishes with one result, which keeps the test case of the container that passed
        expect(submission.results).toHaveLength(1);
        expect(outcomesOf(result, testNames)).toEqual({ [PASSING]: true });
        // but a container that did not run through fails the build
        expect(result.successful).toBe(false);
        expect(submission.buildFailed).toBe(true);

        // only the failed container keeps its build log, labelled with its name
        const logsResponse = await page.request.get(`api/programming/participations/${participation.id}/buildlogs`);
        expect(logsResponse.ok()).toBe(true);
        const containerNames = new Set(((await logsResponse.json()) as BuildLogEntry[]).map((entry) => entry.containerName));
        expect([...containerNames]).toEqual([TIMED_OUT_CONTAINER]);

        // which the student sees in the build output of the online editor
        await page.goto(`/courses/${course.id}/exercises/programming-exercises/${exerciseId}/code-editor/${participation.id}`);
        await expect(page.getByTestId('cardBuildOutput').getByText(TIMED_OUT_CONTAINER)).toBeVisible();
    });
});
