import dayjs from 'dayjs';
import fs from 'node:fs';
import { createHash } from 'node:crypto';
import { execSync } from 'node:child_process';
import path from 'node:path';
import { Browser, expect, Page } from '@playwright/test';

import { test } from '../../../support/fixtures';
import { Commands } from '../../../support/commands';
import { admin, instructor } from '../../../support/users';
import { SEED_COURSES } from '../../../support/seedData';
import { ProgrammingLanguage } from '../../../support/constants';
import { escapeRegExp, fillDateTimePicker, generateUUID, newBrowserPage } from '../../../support/utils';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

const course = { id: SEED_COURSES.programmingManagement.id } as any;
const repoRoot = path.resolve(process.cwd(), '../../..');
const reportDir = path.resolve(process.env.HYPERION_LIVE_REPORT_DIR ?? path.join(repoRoot, '.e2e-local/hyperion-live'));
const concurrentJobs = Number(process.env.HYPERION_LIVE_CONCURRENT_JOBS ?? '1');
const liveLlmTimeoutMs = Number(process.env.HYPERION_LIVE_LLM_TIMEOUT_MS ?? '480000');
const concurrentRunId = process.env.HYPERION_LIVE_RUN_ID ?? 'local';
const runReportDir = path.join(reportDir, concurrentRunId);
const scenarioFilter = process.env.HYPERION_LIVE_SCENARIOS?.split(',').filter(Boolean);

type GenerationStatus = {
    jobId?: string;
    running?: boolean;
    mode?: 'GENERATE' | 'ADAPT';
    events?: {
        type: 'STARTED' | 'PROGRESS' | 'DONE' | 'CANCELLED' | 'ERROR';
        message?: string;
        completionStatus?: 'SUCCESS' | 'NEEDS_REVIEW' | 'PARTIAL';
        verdict?: { mechanicallyVerified?: boolean; solutionPassed?: boolean; templateFailed?: boolean; testCount?: number; reasons?: string[] };
        liveExerciseChanged?: boolean;
        savedRepositoryCommits?: Record<string, string>;
    }[];
    // Production emits only a lightweight per-file change log (no file content) for reconnect replay; the produced content itself is always read back via the persisted-exercise
    // REST endpoints below, since a mechanically valid candidate is now saved to the live exercise for both SUCCESS and NEEDS_REVIEW (see the terminal-status handling further down).
    fileChanges?: { type: 'FILE_CHANGE'; path: string; repo: 'solution' | 'template' | 'tests' | 'other'; action: 'write' | 'edit' | 'delete'; turn: number; timestamp: string }[];
    // The staged generation's stage-0 DESIGN.md, surfaced to the run's owner once the outcome lands. Omitted for non-owner/sanitized status views or when the run never
    // produced one (e.g. it errored before staging finished).
    designDocument?: string;
    specDocument?: string;
};

type Scenario = {
    id: string;
    complexity: 'introductory' | 'intermediate' | 'advanced';
    requirements: string;
    runWholeExercise: boolean;
    // When present, the scenario skips the AI draft step (draftProblemStatementViaUi) and instead seeds this text directly into the problem-statement editor, for
    // exercising generation against an instructor-authored (or otherwise pre-baked) statement rather than a freshly drafted one. See seedProblemStatementViaUi.
    seedProblemStatement?: string;
    // When true, the scenario neither drafts nor seeds a statement: generation starts from a BLANK problem statement so the server-side SPEC stage fires
    // (specStageApplies is true only when no non-trivial statement exists). The brief still travels via #userPrompt.
    noDraft?: boolean;
};

type ProblemStatementAssessment = ReturnType<typeof assessProblemStatement>;
type GeneratedExerciseAssessment = ReturnType<typeof assessGeneratedExercise>;

const scenarios: Scenario[] = [
    {
        id: 'diverse-recursion',
        complexity: 'intermediate',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an intermediate Java exercise that teaches recursion. Students implement several recursive methods over numbers and strings. Clearly describe each method and how it should behave.',
    },
    {
        id: 'diverse-streams',
        complexity: 'intermediate',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an intermediate Java exercise that teaches the Java Streams API and lambdas. Students filter, transform, and aggregate a small collection of records. Clearly describe the operations.',
    },
    {
        id: 'diverse-encapsulation',
        complexity: 'intermediate',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an intermediate Java exercise that teaches encapsulation and exceptions with a small stateful class such as a bank account. Students implement deposit, withdraw, and balance with validation. Clearly describe the rules.',
    },
    {
        id: 'diverse-inheritance',
        complexity: 'intermediate',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an intermediate Java exercise that teaches inheritance and polymorphism with an abstract base class and several subclasses, for example geometric shapes with an area method. Clearly describe each type.',
    },
    {
        id: 'diverse-generics',
        complexity: 'intermediate',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an intermediate Java exercise that teaches generics by implementing a simple generic data structure such as a stack. Clearly describe the operations and their behavior on edge cases.',
    },
    {
        id: 'diverse-enum-dispatch',
        complexity: 'intermediate',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an intermediate Java exercise that teaches enums that carry behaviour. Students implement an enum whose constants each compute something different. Clearly describe each constant.',
    },
    {
        id: 'diverse-string-parsing',
        complexity: 'intermediate',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an intermediate Java exercise about parsing and formatting structured text. Students implement methods that read a small text format and produce a formatted summary. Clearly describe the format and the edge cases.',
    },
    {
        id: 'diverse-collections',
        complexity: 'intermediate',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an intermediate Java exercise about collections. Students group and aggregate items into maps and lists. Clearly describe the grouping rules and the ordering.',
    },
    {
        id: 'diverse-interface-default',
        complexity: 'intermediate',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an intermediate Java exercise that teaches interfaces with default methods. Students implement an interface and override some default behaviour. Clearly describe each method.',
    },
    {
        id: 'diverse-intro-conditionals',
        complexity: 'introductory',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create a short introductory Java exercise about branching and comparison. Students classify a small set of numeric readings into categories with clearly stated boundaries. Describe every boundary explicitly, including what happens exactly on it.',
    },
    {
        id: 'diverse-intro-formatting',
        complexity: 'introductory',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create a short introductory Java exercise about formatting numbers as human-readable text. Students round amounts to a fixed number of decimals and render them with a unit. Describe the rounding rule, negative values, and zero.',
    },
    {
        id: 'diverse-state-machine',
        complexity: 'intermediate',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an intermediate Java exercise about a small state machine, for example an order that moves through a fixed set of states. Some transitions are legal and some are not. Describe every legal transition and what must happen when an illegal one is attempted.',
    },
    {
        id: 'diverse-adv-graph-cycles',
        complexity: 'advanced',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an advanced Java exercise about ordering tasks that depend on each other. Students produce a valid execution order for a dependency graph and detect when no such order exists. Describe the ordering rule, how ties are broken, and exactly what happens for a graph that cannot be ordered.',
    },
    {
        id: 'diverse-adv-immutable-value',
        complexity: 'advanced',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an advanced Java exercise about designing an immutable value type that holds a collection. Students implement construction, equality, and derived operations that return new instances. Describe what must remain unchanged when callers mutate the data they passed in or the data they receive back.',
    },
    {
        id: 'diverse-adv-interval-merge',
        complexity: 'advanced',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an advanced Java exercise about combining overlapping ranges into a minimal set of non-overlapping ranges. Describe precisely how touching ranges, fully contained ranges, and empty input are treated, and whether range ends are inclusive or exclusive.',
    },
    {
        id: 'bicycle-share-summary',
        complexity: 'introductory',
        runWholeExercise: true,
        requirements:
            'Create a compact introductory Java exercise about summarizing bicycle-share trips. Each trip has a station identifier and a duration in minutes. Students should calculate the total duration of valid trips, count trips longer than 30 minutes, and aggregate the number of valid departures per station. Durations less than or equal to zero are invalid and ignored. Cover empty input, repeated stations, and the exact 30-minute boundary. Do not prescribe class names, method names, or an implementation strategy.',
    },
    {
        id: 'playlist-strategy-uml',
        complexity: 'intermediate',
        runWholeExercise: true,
        requirements:
            'Create an intermediate Java exercise that teaches the strategy design pattern with music playlists. A track has a title, an artist, a rating from 1 to 5, and a duration in seconds; provide the track and playlist data types as given code. Students define the common playback-order strategy interface themselves, implement two interchangeable strategies — insertion order as given, and by rating with the highest rating first while ties keep insertion order — and a small player class that returns the playback order via the selected strategy. Include a UML class diagram of the intended design in the problem statement and link its elements to the checks. Cover empty playlists, a single track, and rating ties. Everything must be deterministic — no randomness, shuffling, or time.',
    },
    {
        id: 'strategy-nodraft-spec',
        complexity: 'intermediate',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an intermediate Java exercise that teaches the strategy design pattern. Use a non-standard theme for the strategies to keep it interesting. Clearly describe which strategies should exist and how they work. Students should design and create the strategy interface and the concrete strategy classes themselves, and wire them into a provided context class.',
    },
    {
        id: 'strategy-authentic-exact-brief',
        complexity: 'intermediate',
        runWholeExercise: true,
        noDraft: true,
        requirements:
            'Create an intermediate Java exercise that teaches the strategy design pattern. Use a non-standard theme for the strategies to keep it interesting. Clearly describe which strategies should exist and how they work.',
    },
    {
        id: 'strategy-nonstandard-v2',
        complexity: 'intermediate',
        runWholeExercise: true,
        requirements:
            'Create an intermediate Java exercise that teaches the strategy design pattern. Use a non-standard theme for the strategies to keep it interesting. Clearly describe which strategies should exist and how they work. Students should design and create the strategy interface and the concrete strategy classes themselves, and wire them into a provided context class.',
    },
    {
        id: 'strategy-nonstandard-theme',
        complexity: 'intermediate',
        runWholeExercise: true,
        requirements:
            'Create an intermediate Java exercise that teaches the strategy design pattern. Use a non-standard theme for the strategies to keep it interesting. Clearly describe which strategies should exist and how they work.',
    },
    {
        id: 'strategy-pattern-open',
        complexity: 'intermediate',
        runWholeExercise: true,
        requirements: 'Create an intermediate Java exercise that teaches the strategy design pattern with a less common example.',
    },
    {
        id: 'seeded-strategy-elevator',
        complexity: 'intermediate',
        runWholeExercise: true,
        requirements: 'Use the prepared draft problem statement as the authoritative specification and build the exercise from it.',
        seedProblemStatement: [
            '# Elevator Dispatch Strategies',
            '',
            '## Introduction',
            'An office building runs several elevators. When a passenger calls an elevator from a floor, a dispatch strategy decides which elevator answers. In this exercise you implement the Strategy design pattern: two interchangeable dispatch strategies behind a common interface, and a dispatcher that delegates to the selected strategy and can switch it at runtime.',
            '',
            '## Requirements',
            '- An elevator is described by an identifier and its current floor. The building has a fixed list of elevators; the list order is the registration order.',
            '- Nearest-elevator strategy: answer with the elevator whose current floor is closest to the call floor (absolute distance). If two elevators are equally close, the one earlier in registration order answers.',
            '- Round-robin strategy: answer calls in registration order, one elevator per call, restarting from the first elevator after the last; the current floor is ignored.',
            '- The dispatcher holds the elevator list and the active strategy, answers a call by delegating to the strategy, and can switch strategies at any time. Switching strategies must not reset the round-robin position.',
            '- Selecting an elevator does not move it: dispatch decides, it does not simulate travel.',
            '',
            '## Boundary behavior',
            '- A call with an empty elevator list is rejected as illegal.',
            '- A call floor may equal an elevator floor: distance 0 wins immediately, ties still resolve by registration order.',
            '',
            '## Worked example',
            'Elevators registered as A on floor 1, B on floor 6, C on floor 4. Nearest for a call on floor 5: distances are A=4, B=1, C=1, so B and C tie and B answers (earlier registration). Round-robin for three calls in a row: A, then B, then C, regardless of floors.',
        ].join('\n'),
    },
    {
        id: 'library-checkout-rules',
        complexity: 'introductory',
        runWholeExercise: true,
        requirements:
            'Create a Java exercise for first-year students about applying small business rules to a list of library checkout events. Students should compute useful summaries such as overdue counts, total fees, and member-specific activity. Keep the domain realistic and compact. Include representative edge cases such as empty input, duplicate members, and invalid durations. Do not assume any particular class or method names from me.',
    },
    {
        id: 'robot-rover-state',
        complexity: 'intermediate',
        runWholeExercise: true,
        requirements:
            'Create a Java exercise where students implement the state changes of a small robot rover on a bounded grid. It should practice parsing commands, updating position and orientation, rejecting invalid input, and handling obstacles. The exercise should be testable with deterministic examples and should not require graphics, randomness, networking, or time. You may choose the public API that best teaches the concept.',
    },
    {
        id: 'event-scheduler-conflicts',
        complexity: 'advanced',
        runWholeExercise: false,
        requirements:
            'Draft a Java programming exercise about a tiny event scheduler that checks conflicts, recurring reminders, and boundary cases around inclusive and exclusive times. The goal is to teach careful API contracts and edge-case reasoning. Avoid prescribing an implementation strategy or exact class names; choose a clean minimal design for students.',
    },
    {
        id: 'warehouse-batch-allocation',
        complexity: 'intermediate',
        runWholeExercise: false,
        requirements:
            'Create a Java exercise about allocating incoming warehouse orders to finite inventory batches. Students should reason about partial fulfillment, stable ordering, invalid quantities, and immutable result summaries. Keep the public contract compact and deterministic, with realistic examples, but do not prescribe data structures or algorithms.',
    },
    {
        id: 'transit-fare-ledger',
        complexity: 'advanced',
        runWholeExercise: false,
        requirements:
            'Create a Java exercise about a transit fare ledger that applies transfers, daily caps, and rejected journeys to a chronological stream of tap events. It should teach precise state transitions and boundary conditions without networking, databases, clocks, or currency floating-point mistakes. Choose a minimal teachable API rather than dictating an implementation.',
    },
];

if (!Number.isInteger(concurrentJobs) || concurrentJobs < 1 || concurrentJobs > scenarios.length) {
    throw new Error(`HYPERION_LIVE_CONCURRENT_JOBS must be between 1 and ${scenarios.length}.`);
}
if (!Number.isFinite(liveLlmTimeoutMs) || liveLlmTimeoutMs < 180_000) {
    throw new Error('HYPERION_LIVE_LLM_TIMEOUT_MS must be at least 180000.');
}
if (!/^[A-Za-z0-9._-]+$/.test(concurrentRunId)) {
    throw new Error('HYPERION_LIVE_RUN_ID may contain only letters, numbers, dots, underscores, and hyphens.');
}
if (concurrentJobs > 1 && !process.env.HYPERION_LIVE_RUN_ID) {
    throw new Error('Concurrent live runs require a unique HYPERION_LIVE_RUN_ID so stale barrier files cannot satisfy the test.');
}

const filteredScenarios = scenarioFilter ? scenarios.filter((scenario) => scenarioFilter.includes(scenario.id)) : scenarios;
if (concurrentJobs > filteredScenarios.length) {
    throw new Error(`HYPERION_LIVE_CONCURRENT_JOBS=${concurrentJobs} requires at least that many selected scenarios.`);
}
const selectedScenarios = concurrentJobs > 1 ? filteredScenarios.slice(0, concurrentJobs).map((scenario) => ({ ...scenario, runWholeExercise: true })) : filteredScenarios;
const selectedScenarioIds = selectedScenarios.map((scenario) => scenario.id);

test.describe('Hyperion live LLM browser E2E qualitative validation', { tag: '@slow' }, () => {
    test.skip(process.env.HYPERION_LLM_MODE !== 'live', 'Live Hyperion UI tests require HYPERION_LLM_MODE=live.');
    test.describe.configure({ mode: concurrentJobs > 1 ? 'parallel' : 'serial', retries: 0 });
    test.use({ serviceWorkers: 'block' });

    for (const scenario of selectedScenarios) {
        test(`drafts a problem statement${scenario.runWholeExercise ? ' and runs full exercise generation' : ''}: ${scenario.id}`, async ({
            page,
            browser,
            login,
            exerciseAPIRequests,
            programmingExerciseCreation,
        }) => {
            test.setTimeout(scenario.runWholeExercise ? 2_400_000 : 240_000);
            await ensureReportDir();
            await assertHyperionGenerationAvailable(page);
            let exercise: ProgrammingExercise | undefined;
            let jobId: string | undefined;
            let scenarioError: unknown;
            const cleanupErrors: unknown[] = [];
            const report: Record<string, unknown> = {
                scenario,
                metadata: liveReportMetadata(),
                startedAt: new Date().toISOString(),
            };

            try {
                await login(instructor, `/course-management/${course.id}/programming-exercises/new`);
                await programmingExerciseCreation.waitForFormToLoad();
                await programmingExerciseCreation.changeEditMode();
                await programmingExerciseCreation.setProgrammingLanguage(ProgrammingLanguage.JAVA);
                await page.locator('#field_projectType').getByText('Maven', { exact: true }).click();
                // The package name is deliberately NOT set here: the create flow derives a topic-appropriate proposal
                // from the AI draft's title, and this test asserts that proposal end-to-end (field prefill below,
                // generated source packages after generation). The title/short name stay unique per run because
                // Artemis requires course-unique values and failed runs may leave exercises behind.
                await programmingExerciseCreation.setTitle(process.env.HYPERION_CHECKPOINT_EXERCISE_TITLE ?? `hyp-live-${generateUUID()}`);
                await programmingExerciseCreation.setShortName(process.env.HYPERION_CHECKPOINT_EXERCISE_SHORT_NAME ?? `hyplive${generateUUID()}`);
                await programmingExerciseCreation.setPoints(100);
                await page.locator('#field_bonusPoints').fill('0');
                await fillDateTimePicker(page.getByLabel('Release Date', { exact: true }), dayjs().add(2, 'days'));
                await programmingExerciseCreation.setDueDate(dayjs().add(3, 'days'));

                if (process.env.HYPERION_CHECKPOINT_EXERCISE_STATEMENT !== undefined) {
                    const seeded = await seedProblemStatementViaUi(page, process.env.HYPERION_CHECKPOINT_EXERCISE_STATEMENT);
                    report.draftProblemStatement = seeded;
                    report.checkpointProblemStatement = true;
                } else if (scenario.noDraft) {
                    // No draft, no seed: the statement stays blank so the server's SPEC stage owns the content decision. The brief goes through the same #userPrompt
                    // field the generate request reads; the package is set manually because the prefill only fires off a draft response.
                    await expect(page.locator('#userPrompt')).toBeVisible({ timeout: 90_000 });
                    await page.locator('#userPrompt').fill(scenario.requirements);
                    await programmingExerciseCreation.setPackageName('de.tum.cit.aet.nodraft');
                    report.proposedPackageName = 'de.tum.cit.aet.nodraft';
                    report.noDraft = true;
                } else if (scenario.seedProblemStatement) {
                    const seeded = await seedProblemStatementViaUi(page, scenario.seedProblemStatement);
                    report.draftProblemStatement = seeded;
                    report.seededProblemStatement = true;
                    // A seeded statement never goes through the draft-derived package-name proposal (that prefill only fires off the draft response), so fill it
                    // explicitly to keep the form valid; keep it in sync with the package assertion below.
                    await programmingExerciseCreation.setPackageName('seededexercise');
                    report.proposedPackageName = 'seededexercise';
                } else {
                    const draft = await draftProblemStatementViaUi(page, scenario.requirements);
                    report.draftProblemStatement = draft;
                    report.draftAssessment = assessProblemStatement(draft);
                    report.proposedPackageName = await expectDraftDerivedPackageNamePrefill(page, draft);
                    if (process.env.HYPERION_LIVE_ALLOW_QUALITY_FINDINGS !== 'true') {
                        assertDraftQuality(report.draftAssessment as ProblemStatementAssessment, scenario.requirements);
                    }
                }
                if (process.env.HYPERION_CHECKPOINT_EXERCISE_PACKAGE) {
                    await programmingExerciseCreation.setPackageName(process.env.HYPERION_CHECKPOINT_EXERCISE_PACKAGE);
                    report.proposedPackageName = process.env.HYPERION_CHECKPOINT_EXERCISE_PACKAGE;
                }

                if (scenario.runWholeExercise) {
                    const generation = await createAndGenerateExerciseViaUi(
                        page,
                        () => waitForConcurrentStart(scenario.id),
                        Boolean(scenario.seedProblemStatement || process.env.HYPERION_CHECKPOINT_EXERCISE_STATEMENT !== undefined),
                        async (createdExercise) => {
                            exercise = createdExercise;
                            report.createdExercise = {
                                id: createdExercise.id,
                                programmingLanguage: createdExercise.programmingLanguage,
                                projectType: createdExercise.projectType,
                                releaseDate: createdExercise.releaseDate,
                                numberOfParticipations: createdExercise.numberOfParticipations,
                                studentParticipationCount: createdExercise.studentParticipations?.length,
                            };
                        },
                    );
                    const generatedExercise = generation.exercise;
                    exercise = generatedExercise;
                    jobId = generation.jobId;
                    report.exerciseId = generatedExercise.id;
                    report.title = generatedExercise.title;
                    report.setupRequest = generation.setupRequest;
                    report.generationRequest = generation.request;
                    if (scenario.seedProblemStatement || process.env.HYPERION_CHECKPOINT_EXERCISE_STATEMENT !== undefined) {
                        expect(generation.request.prompt?.trim() ?? '', 'A prepared statement must reach generation without a competing source brief').toBe('');
                    }
                    await assertConcurrentJobsOverlap(page, scenario.id, generatedExercise.id!, jobId);
                    await expect(page.getByTestId('hyperion-generation-activity')).toBeVisible({ timeout: 60_000 });
                    await expect(page.getByTestId('hyperion-generation-persistence-state')).toContainText('Agent working copy', { timeout: 60_000 });
                    await expect(page.getByTestId('hyperion-generation-current-progress')).toBeVisible({ timeout: 120_000 });
                    await expectLiveActivityRehydration(browser, generatedExercise, jobId);
                    await expect(page.getByTestId('hyperion-generation-live-status')).toBeVisible({ timeout: 60_000 });
                    const terminalStatus = await waitForTerminalStatus(page, generatedExercise.id!, jobId);
                    report.terminalStatus = terminalStatus;
                    report.designDocument = terminalStatus.designDocument;
                    report.specDocument = terminalStatus.specDocument;
                    expect(terminalStatus.terminal?.type).toBe('DONE');
                    expect(['SUCCESS', 'NEEDS_REVIEW']).toContain(terminalStatus.terminal?.completionStatus);
                    if (terminalStatus.terminal?.completionStatus === 'SUCCESS') {
                        expect(terminalStatus.terminal?.verdict?.mechanicallyVerified).toBe(true);
                    } else {
                        expect(typeof terminalStatus.terminal?.verdict?.mechanicallyVerified).toBe('boolean');
                    }
                    // Current save policy: a mechanically valid candidate is saved to the live exercise for BOTH SUCCESS and NEEDS_REVIEW — only unresolved spec-fidelity findings
                    // change the completion label and require instructor attention; they never hold the candidate back in a throwaway draft. So liveExerciseChanged, revert
                    // availability, and the changed-files review affordances are asserted the same way for both outcomes below; only the persistence-state wording differs.
                    expect(terminalStatus.terminal?.liveExerciseChanged).toBe(true);
                    report.persistedExercise = await fetchPersistedExercise(page, generatedExercise.id!);
                    report.repositories = await fetchRepositorySummaries(page, generatedExercise.id!);
                    const persistedPackageName = (report.persistedExercise as { packageName?: string }).packageName;
                    expect(persistedPackageName).toBe(report.proposedPackageName);
                    assertGeneratedSourcesUseExercisePackage(report.repositories as Awaited<ReturnType<typeof fetchRepositorySummaries>>, persistedPackageName!);
                    const candidateProblemStatement = (report.persistedExercise as any).problemStatement;
                    report.candidateProblemStatement = candidateProblemStatement;
                    report.generatedAssessment = assessGeneratedExercise({ problemStatement: candidateProblemStatement }, report.repositories as any, terminalStatus);
                    const isFullSuccess = terminalStatus.terminal?.completionStatus === 'SUCCESS';
                    if (!isFullSuccess) {
                        expect(terminalStatus.terminal?.message).toMatch(/review/i);
                    } else if (process.env.HYPERION_LIVE_ALLOW_QUALITY_FINDINGS !== 'true') {
                        // Quality heuristics stay hard gates only outside capture mode — in capture mode (ALLOW_QUALITY_FINDINGS)
                        // they are recorded in the report as findings, consistent with how assertDraftQuality is handled above.
                        assertGeneratedExerciseQuality(report.generatedAssessment as GeneratedExerciseAssessment);
                    }
                    await openAiActivity(page);
                    await expect(page.getByTestId('hyperion-generation-activity').getByRole('status')).toContainText('Generation finished', { timeout: 60_000 });
                    await expect(page.getByTestId('hyperion-generation-persistence-state')).toContainText(
                        isFullSuccess ? 'ready for instructor review' : 'instructor review required',
                        { timeout: 60_000 },
                    );
                    await expect(page.getByTestId('hyperion-generation-verdict')).toBeVisible();
                    await expect(page.getByTestId('hyperion-generation-review')).toBeVisible();
                    const reviewTargets = await page
                        .getByTestId('hyperion-generation-review-action')
                        .evaluateAll((buttons) => buttons.map((button) => button.getAttribute('data-review-target')).filter(Boolean));
                    expect(reviewTargets.sort()).toEqual(['problem-statement', 'solution', 'template', 'tests']);
                    await expect(page.getByTestId('hyperion-generation-revert')).toBeVisible();
                    await expect(page.getByTestId('hyperion-generation-run-again')).toHaveCount(0);
                    await page.getByTestId('hyperion-generation-details-toggle').click();
                    await expect(page.getByTestId('hyperion-changed-files')).toBeVisible();
                    expect(await page.getByTestId('hyperion-generation-file').count()).toBeGreaterThan(0);
                    await expect(page.getByTestId('hyperion-ai-menu')).toBeEnabled({ timeout: 60_000 });
                    jobId = undefined;
                    if (process.env.HYPERION_LIVE_ALLOW_QUALITY_FINDINGS !== 'true') {
                        expect(terminalStatus.terminal?.completionStatus).toBe('SUCCESS');
                        expect(terminalStatus.terminal?.verdict?.mechanicallyVerified).toBe(true);
                    }
                }
            } catch (error) {
                scenarioError = error;
                report.error = serializeError(error);
                await markConcurrentFailure(scenario.id, error).catch(() => undefined);
            } finally {
                if (!jobId && exercise?.id) {
                    try {
                        const status = await getGenerationStatus(page, exercise.id);
                        if (status.running && status.jobId) {
                            jobId = status.jobId;
                        }
                    } catch (error) {
                        cleanupErrors.push(error);
                    }
                }
                if (jobId && exercise?.id) {
                    try {
                        const response = await page.request.delete(`/api/hyperion/programming-exercises/${exercise.id}/generate-exercise/jobs/${jobId}`);
                        if (!response.ok() && response.status() !== 404) {
                            cleanupErrors.push(new Error(`Generation job cleanup returned HTTP ${response.status()}.`));
                        }
                    } catch (error) {
                        cleanupErrors.push(error);
                    }
                }
                if (exercise?.id) {
                    try {
                        await login(admin);
                        await exerciseAPIRequests.deleteProgrammingExercise(exercise.id);
                        const deletedExercise = await page.request.get(`/api/programming/programming-exercises/${exercise.id}`);
                        expect(deletedExercise.status()).toBe(404);
                    } catch (error) {
                        cleanupErrors.push(error);
                    }
                }
                report.finishedAt = new Date().toISOString();
                report.cleanupErrors = cleanupErrors.map(serializeError);
                try {
                    await writeScenarioReport(scenario.id, report);
                } catch (error) {
                    cleanupErrors.push(error);
                }
            }
            if (scenarioError) {
                throw scenarioError;
            }
            if (cleanupErrors.length > 0) {
                throw cleanupErrors[0];
            }
        });
    }
});

async function assertHyperionGenerationAvailable(page: Page) {
    const response = await page.request.get('/management/info');
    expect(response.ok(), 'Hyperion live generation E2E requires a reachable Artemis instance.').toBeTruthy();
    const info = await response.json();
    expect(info.activeModuleFeatures, 'Hyperion generation is not enabled in this E2E environment.').toContain('hyperion');
}

async function openAiActivity(page: Page) {
    if (await page.getByTestId('hyperion-generation-activity').isVisible()) {
        return;
    }
    await page.getByTestId('hyperion-ai-menu').click();
    await page.getByRole('tab', { name: 'AI activity' }).click();
    await expect(page.getByTestId('hyperion-generation-activity')).toBeVisible({ timeout: 60_000 });
}

async function expectLiveActivityRehydration(browser: Browser, exercise: ProgrammingExercise, jobId: string) {
    const freshPage = await newBrowserPage(browser);
    try {
        await Commands.login(
            freshPage,
            instructor,
            `/course-management/${course.id}/programming-exercises/${exercise.id}/code-editor/TEMPLATE/${exercise.templateParticipation!.id}`,
        );
        await expect(freshPage.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
        await openAiActivity(freshPage);
        await expect(freshPage.getByTestId('hyperion-generation-live-status')).toBeVisible({ timeout: 60_000 });
        const status = await getGenerationStatus(freshPage, exercise.id!);
        expect(status.jobId).toBe(jobId);
        const terminal = [...(status.events ?? [])].reverse().find((event) => ['DONE', 'ERROR', 'CANCELLED'].includes(event.type));
        if (terminal) {
            await expect(freshPage.getByTestId('hyperion-generation-terminal-status')).toBeVisible({ timeout: 60_000 });
        } else {
            expect(status.running).toBe(true);
            await expect(freshPage.getByTestId('hyperion-generation-current-progress')).toBeVisible({ timeout: 60_000 });
        }
    } finally {
        await freshPage.context().close();
    }
}

async function draftProblemStatementViaUi(page: Page, requirements: string) {
    await expect(page.locator('#userPrompt')).toBeVisible({ timeout: 90_000 });
    await page.locator('#userPrompt').fill(requirements);
    const draftResponsePromise = page.waitForResponse(
        (response) => response.request().method() === 'POST' && response.url().includes(`/api/hyperion/courses/${course.id}/problem-statements/generate`),
        { timeout: liveLlmTimeoutMs },
    );
    await page.getByRole('button', { name: 'Generate Draft Problem Statement' }).click();
    const draftResponse = await draftResponsePromise;
    expect(draftResponse.ok()).toBeTruthy();
    const body = (await draftResponse.json()) as { draftProblemStatement?: string };
    expect(body.draftProblemStatement?.trim()).toBeTruthy();
    await expect(page.getByText('Problem statement has been successfully generated.')).toBeVisible({ timeout: 30_000 });
    const draft = body.draftProblemStatement!.trim();
    await expect.poll(() => readProblemStatementEditor(page), { timeout: 30_000 }).toBe(draft);
    return draft;
}

/**
 * Seeds the problem-statement Monaco editor directly with a caller-supplied statement, bypassing the AI draft round-trip (draftProblemStatementViaUi). Locates the
 * live editor instance the same way readProblemStatementEditor does, then calls Monaco's own setValue through the page so the change fires like real typing: the
 * editor's onDidChangeModelContent listener emits the (textChanged) output, which the Angular form consumes the same as any other edit.
 */
async function seedProblemStatementViaUi(page: Page, seedText: string): Promise<string> {
    // The instructions editor only renders once a statement exists, so seed through the same channel a real
    // draft uses: fulfill the draft request with the prepared statement and let the app's own pipeline
    // (editor setText, metadata prefill, change handling) place it — faithful to the UI, no Monaco poking.
    await page.route('**/api/hyperion/courses/*/problem-statements/generate', async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ draftProblemStatement: seedText }) });
    });
    await expect(page.locator('#userPrompt')).toBeVisible({ timeout: 90_000 });
    await page.locator('#userPrompt').fill('Seeded intermediate: the prepared statement below is authoritative.');
    await page.getByRole('button', { name: 'Generate Draft Problem Statement' }).click();
    await expect.poll(() => readProblemStatementEditor(page), { timeout: 60_000 }).toBe(seedText.trim());
    await page.unroute('**/api/hyperion/courses/*/problem-statements/generate');
    return seedText.trim();
}

/**
 * Asserts that the create form's package-name field was prefilled with the topic-derived proposal after drafting,
 * by mirroring the deterministic derivation in problem-statement.utils.ts (title heading -> single lowercase segment).
 */
async function expectDraftDerivedPackageNamePrefill(page: Page, draft: string): Promise<string> {
    const packageField = page.locator('#field_packageName');
    await expect(packageField).not.toHaveValue('', { timeout: 15_000 });
    const value = await packageField.inputValue();
    const title =
        draft
            .match(/^#\s+(.+)$/m)?.[1]
            ?.replace(/[*_`~]/g, '')
            .trim() ?? '';
    const slugWords = title
        .normalize('NFKD')
        .replace(/[\u0300-\u036f]/g, '')
        .split(/[^a-zA-Z0-9]+/)
        .filter(Boolean)
        .map((word) => word.toLowerCase());
    // Mirror of deriveProposedPackageName: institutional prefix, then whole words only up to the cap, never a mid-word cut.
    const prefix = 'de.tum.cit.aet.';
    const slugBudget = 32 - prefix.length;
    let joinedSlug = '';
    for (const word of slugWords) {
        if (joinedSlug && (joinedSlug + word).length > slugBudget) {
            break;
        }
        joinedSlug += word;
    }
    const expectedSlug = joinedSlug.replace(/^[0-9]+/, '').substring(0, slugBudget);
    expect([`${prefix}${expectedSlug}`, `${prefix}${expectedSlug}exercise`]).toContain(value);
    return value;
}

/** Every generated Java source must live in and declare the exercise's package — the invariant the agent is prompted with and the integrity gate enforces server-side. */
function assertGeneratedSourcesUseExercisePackage(repositories: { selectedContent?: Record<string, Record<string, string>> }, packageName: string) {
    const packagePath = packageName.replace(/\./g, '/');
    const packageDeclaration = new RegExp(`^\\s*package\\s+${escapeRegExp(packageName)}\\s*;`, 'm');
    for (const repo of ['solution', 'template', 'tests'] as const) {
        const files = repositories.selectedContent?.[repo] ?? {};
        expect(Object.keys(files).length, `${repo} repository contains no Java sources`).toBeGreaterThan(0);
        for (const [file, content] of Object.entries(files)) {
            expect(file, `${repo}/${file} is outside the exercise package ${packageName}`).toContain(packagePath);
            expect(content, `${repo}/${file} does not declare package ${packageName}`).toMatch(packageDeclaration);
        }
    }
}

async function createAndGenerateExerciseViaUi(
    page: Page,
    beforeStart: () => Promise<void>,
    statementIsAuthoritative: boolean,
    onExerciseCreated: (exercise: ProgrammingExercise) => Promise<void>,
) {
    await beforeStart();
    await expect(page.locator('#generate-with-ai')).toBeEnabled({ timeout: 30_000 });
    const setupResponsePromise = page.waitForResponse(
        (response) => response.request().method() === 'POST' && response.url().includes('/api/programming/programming-exercises/setup?emptyRepositories=true'),
        { timeout: liveLlmTimeoutMs },
    );
    const startResponsePromise = page.waitForResponse(
        (response) => response.request().method() === 'POST' && /\/api\/hyperion\/programming-exercises\/\d+\/generate-exercise$/.test(response.url()),
        { timeout: liveLlmTimeoutMs },
    );
    const generationEndpoint = '**/api/hyperion/programming-exercises/*/generate-exercise';
    if (statementIsAuthoritative) {
        // The prepared statement is injected through the real draft UI so Angular creates the same exercise state as a user-authored statement. That UI also remembers its
        // fixture-only draft trigger as a source brief. Strip only that transport artifact: a nonblank brief deliberately enables concept/SPEC generation and would be allowed
        // to replace the prepared statement, defeating what this scenario is meant to exercise.
        await page.route(generationEndpoint, async (route) => {
            const request = route.request();
            const body = request.postDataJSON() as { mode: string; prompt?: string };
            await route.continue({ postData: JSON.stringify({ ...body, prompt: '' }) });
        });
    }
    await page.locator('#generate-with-ai').click();
    const setupResponse = await setupResponsePromise;
    expect(setupResponse.ok()).toBeTruthy();
    const exercise = (await setupResponse.json()) as ProgrammingExercise;
    expect(exercise.id).toBeDefined();
    expect(exercise.templateParticipation?.id).toBeDefined();
    await onExerciseCreated(exercise);
    await expect(page).toHaveURL(new RegExp(`/programming-exercises/${exercise.id}/code-editor/TEMPLATE/${exercise.templateParticipation!.id}`));
    const startResponse = await startResponsePromise;
    if (statementIsAuthoritative) {
        await page.unroute(generationEndpoint);
    }
    expect(startResponse.status()).toBe(202);
    const body = (await startResponse.json()) as { jobId?: string };
    expect(body.jobId).toBeTruthy();
    return {
        exercise,
        jobId: body.jobId!,
        setupRequest: setupResponse.request().postDataJSON(),
        request: startResponse.request().postDataJSON(),
    };
}

async function readProblemStatementEditor(page: Page): Promise<string | undefined> {
    const editor = page.locator('jhi-programming-exercise-editable-instructions .monaco-editor').first();
    await editor.waitFor({ state: 'visible' });
    const editorHandle = await editor.elementHandle();
    if (!editorHandle) {
        return undefined;
    }
    return page.evaluate((editorNode) => {
        const editors = (window as any).monaco?.editor?.getEditors?.() ?? [];
        const match = editors.find((candidate: any) => {
            const node = candidate.getDomNode?.();
            return node && (node === editorNode || node.contains(editorNode) || editorNode.contains(node));
        });
        return match?.getValue?.();
    }, editorHandle);
}

async function waitForConcurrentStart(scenarioId: string) {
    if (concurrentJobs <= 1) {
        return;
    }
    const readyDir = path.join(runReportDir, 'barrier', 'ready');
    await fs.promises.mkdir(readyDir, { recursive: true });
    await fs.promises.writeFile(path.join(readyDir, scenarioId), 'ready');
    await waitForConcurrentParticipants(readyDir, selectedScenarioIds, 600_000);
}

async function assertConcurrentJobsOverlap(page: Page, scenarioId: string, exerciseId: number, jobId: string) {
    if (concurrentJobs <= 1) {
        return;
    }
    const jobsDir = path.join(runReportDir, 'barrier', 'jobs');
    await fs.promises.mkdir(jobsDir, { recursive: true });
    await fs.promises.writeFile(path.join(jobsDir, `${scenarioId}.json`), JSON.stringify({ exerciseId, jobId }));
    const expectedJobFiles = selectedScenarioIds.map((id) => `${id}.json`);
    await waitForConcurrentParticipants(jobsDir, expectedJobFiles, 120_000);
    const jobFiles = expectedJobFiles;
    const jobs = await Promise.all(jobFiles.map(async (file) => JSON.parse(await fs.promises.readFile(path.join(jobsDir, file), 'utf8')) as { exerciseId: number; jobId: string }));
    await expect
        .poll(
            async () => {
                const statuses = await Promise.all(jobs.map((job) => getGenerationStatus(page, job.exerciseId)));
                return statuses.filter((status, index) => status.running && status.jobId === jobs[index].jobId && status.events?.some((event) => event.type === 'STARTED')).length;
            },
            { timeout: 120_000, intervals: [250, 500, 1_000] },
        )
        .toBe(concurrentJobs);
}

async function waitForConcurrentParticipants(directory: string, expectedFiles: string[], timeout: number) {
    await expect
        .poll(
            async () => {
                const failures = await fs.promises.readdir(path.join(runReportDir, 'barrier', 'failures')).catch(() => []);
                if (failures.length > 0) {
                    throw new Error(`A concurrent live scenario failed before the barrier completed: ${failures.join(', ')}`);
                }
                const files = await fs.promises.readdir(directory);
                return expectedFiles.every((file) => files.includes(file));
            },
            { timeout, intervals: [250, 500, 1_000] },
        )
        .toBe(true);
}

async function markConcurrentFailure(scenarioId: string, error: unknown) {
    if (concurrentJobs <= 1) {
        return;
    }
    const failuresDir = path.join(runReportDir, 'barrier', 'failures');
    await fs.promises.mkdir(failuresDir, { recursive: true });
    await fs.promises.writeFile(path.join(failuresDir, `${scenarioId}.json`), JSON.stringify(serializeError(error)));
}

async function waitForTerminalStatus(page: Page, exerciseId: number, jobId: string) {
    await expect
        .poll(
            async () => {
                const status = await getGenerationStatus(page, exerciseId);
                const terminal = [...(status.events ?? [])].reverse().find((event) => ['DONE', 'ERROR', 'CANCELLED'].includes(event.type));
                return terminal && status.jobId === jobId
                    ? {
                          jobId: status.jobId,
                          running: status.running,
                          terminal,
                      }
                    : undefined;
            },
            { timeout: 2_400_000, intervals: [5_000, 10_000, 15_000] },
        )
        .toBeDefined();
    const status = await getGenerationStatus(page, exerciseId);
    const terminal = status.jobId === jobId ? [...(status.events ?? [])].reverse().find((event) => ['DONE', 'ERROR', 'CANCELLED'].includes(event.type)) : undefined;
    expect(status.jobId).toBe(jobId);
    expect(terminal).toBeDefined();
    return { ...status, terminal };
}

async function getGenerationStatus(page: Page, exerciseId: number): Promise<GenerationStatus> {
    const response = await page.request.get(`/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/status`);
    expect(response.ok()).toBeTruthy();
    if (response.status() === 204) {
        return {};
    }
    return (await response.json()) as GenerationStatus;
}

async function fetchPersistedExercise(page: Page, exerciseId: number) {
    const response = await page.request.get(`/api/programming/programming-exercises/${exerciseId}`);
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    return {
        title: body.title,
        shortName: body.shortName,
        packageName: body.packageName,
        problemStatement: body.problemStatement,
    };
}

// A mechanically valid candidate is now saved to the live exercise for both SUCCESS and NEEDS_REVIEW (see the terminal-status handling above), so the persisted-exercise REST
// endpoints always reflect the produced content; there is no longer a draft-only state that requires reading back the lightweight fileChanges log instead.
async function fetchRepositorySummaries(page: Page, exerciseId: number) {
    const [solution, template, tests] = await Promise.all([
        fetchRepoFiles(page, `/api/programming/programming-exercises/${exerciseId}/solution-files-content?omitBinaries=true`),
        fetchRepoFiles(page, `/api/programming/programming-exercises/${exerciseId}/template-files-content?omitBinaries=true`),
        fetchTestRepoFiles(page, exerciseId),
    ]);
    return {
        solution: summarizeFiles(solution),
        template: summarizeFiles(template),
        tests: summarizeFiles(tests),
        selectedContent: selectInterestingContent({ solution, template, tests }),
    };
}

async function fetchRepoFiles(page: Page, endpoint: string): Promise<Record<string, string>> {
    const response = await page.request.get(endpoint);
    expect(response.ok()).toBeTruthy();
    return (await response.json()) as Record<string, string>;
}

async function fetchTestRepoFiles(page: Page, exerciseId: number): Promise<Record<string, string>> {
    const list = await page.request.get(`/api/programming/programming-exercises/${exerciseId}/test-repository/files`);
    expect(list.ok()).toBeTruthy();
    const body = (await list.json()) as string[] | Record<string, string>;
    const files = Array.isArray(body)
        ? body
        : Object.entries(body)
              .filter(([, fileType]) => fileType === 'FILE')
              .map(([file]) => file);
    const entries = await Promise.all(
        files
            .filter((file) => file.endsWith('.java'))
            .map(async (file) => {
                const content = await page.request.get(`/api/programming/programming-exercises/${exerciseId}/test-repository/file?file=${encodeURIComponent(file)}`);
                expect(content.ok(), `Could not read persisted test file ${file}: HTTP ${content.status()}`).toBeTruthy();
                return [file, await content.text()] as const;
            }),
    );
    return Object.fromEntries(entries);
}

function summarizeFiles(files: Record<string, string>) {
    return Object.entries(files)
        .filter(([file]) => file.endsWith('.java') || file.endsWith('.md') || file.endsWith('.txt'))
        .map(([file, content]) => ({ file, chars: content.length, lines: content.split('\n').length }));
}

function selectInterestingContent(repos: { solution: Record<string, string>; template: Record<string, string>; tests: Record<string, string> }) {
    const pick = (files: Record<string, string>) =>
        Object.fromEntries(
            Object.entries(files)
                .filter(([file]) => file.endsWith('.java'))
                .map(([file, content]) => [file, content]),
        );
    return { solution: pick(repos.solution), template: pick(repos.template), tests: pick(repos.tests) };
}

function assessProblemStatement(problemStatement: string) {
    const taskBindings = [...problemStatement.matchAll(/\[task]\[[^\]]+\]\((.*)\)\s*$/gm)].map((match) => match[1]);
    const headings = [...problemStatement.matchAll(/^#{1,3}\s+(.+)$/gm)].map((match) => match[1]);
    const codeBlocks = (problemStatement.match(/```/g) ?? []).length / 2;
    const leakedInternals = /\b(agent|sandbox|verifier|template repository|solution repository|test repository|Hyperion)\b/i.test(problemStatement);
    const prescribesSolution = /\b(use\s+(HashMap|ArrayList|stream|recursion)|must\s+implement\s+.*algorithm|time complexity\s+must)\b/i.test(problemStatement);
    const rawTaskMarker = /\[tasks?]/i.test(problemStatement);
    const uml = /@(?:start|end)uml/i.test(problemStatement);
    const legacyStructuralRefs = /\btest(?:Class|Methods|Attributes|Constructors)\[[^\]]+]/i.test(problemStatement);
    const inventedTestMethodNames = [...stripTaskBindings(problemStatement).matchAll(/\btest[A-Z][A-Za-z0-9_]*\s*\(?/g)].map((match) => match[0]);
    return {
        chars: problemStatement.length,
        headings,
        taskCount: taskBindings.length,
        taskBindings,
        codeBlocks,
        hasTitle: /^#\s+\S/m.test(problemStatement),
        hasTasksSection: /##\s+Tasks/i.test(problemStatement),
        hasWorkedExample: /example/i.test(problemStatement) && (codeBlocks > 0 || /\|.+\|.+\|/m.test(problemStatement)),
        leakedInternals,
        prescribesSolution,
        rawTaskMarker,
        uml,
        legacyStructuralRefs,
        inventedTestMethodNames,
        hasReplacementCharacter: problemStatement.includes('\uFFFD'),
        hasAuthoringResidue: /instructor decisions?|before final generation|drafting notes?/i.test(problemStatement),
        optionalHeading:
            /^\s*#{1,6}[^\n]*(?:Optional Challenges?|Extra Credit|\(optional\))/im.test(problemStatement) || /\*\*\(Optional\)|\bif you choose to expose\b/i.test(problemStatement),
        jsonExport: /\bJSON export\b/i.test(problemStatement),
        performanceBenchmark: /\b(?:performance benchmark|benchmarking task|throughput benchmark)\b/i.test(problemStatement),
        publicApiDetails:
            /^\s*#{1,6}\s*Operations?\b/im.test(problemStatement) ||
            /^\s*#{1,6}\s*.*\bAPI\b/im.test(problemStatement) ||
            /\|\s*(?:Method|Operation)\s*\|\s*Purpose/i.test(problemStatement) ||
            /\bnew\s+[A-Z][A-Za-z0-9_]*\s*\(|\b[a-z][A-Za-z0-9_]*\.[a-z][A-Za-z0-9_]*\s*\(/.test(problemStatement),
        signOff: /\bGood luck\b/i.test(problemStatement),
        unrequestedTestingWork: /\b(?:unit tests?|provided test suite|test suite)\b/i.test(problemStatement),
        unrequestedOperationalScope: /\b(?:resource exhaustion|upper limit|maximum recurrence limit|thread-safe|thread safety|concurrent use)\b/i.test(problemStatement),
        contradictoryConflictExample: /\bconflict\b[^\n]{0,160}\bdo\s+\*?\*?not\*?\*?\s+overlap\b[^\n]{0,160}\bno conflict\b/i.test(problemStatement),
    };
}

function assertDraftQuality(assessment: ProblemStatementAssessment, requirements: string) {
    expect(assessment.hasTitle).toBe(true);
    expect(assessment.taskCount).toBe(0);
    expect(assessment.leakedInternals).toBe(false);
    expect(assessment.rawTaskMarker).toBe(false);
    expect(assessment.uml).toBe(false);
    expect(assessment.legacyStructuralRefs).toBe(false);
    expect(assessment.inventedTestMethodNames).toEqual([]);
    if (!/optional|challenge|extra credit/i.test(requirements)) {
        expect(assessment.optionalHeading).toBe(false);
    }
    if (!/json/i.test(requirements)) {
        expect(assessment.jsonExport).toBe(false);
    }
    if (!/performance|benchmark|throughput|time complexity/i.test(requirements)) {
        expect(assessment.performanceBenchmark).toBe(false);
    }
    if (/avoid|do not|don't|without|not assume/i.test(requirements) && /class|method|api|implementation/i.test(requirements)) {
        expect(assessment.publicApiDetails).toBe(false);
    }
    if (!/unit tests?|testing|test suite/i.test(requirements)) {
        expect(assessment.unrequestedTestingWork).toBe(false);
    }
    if (!/resource|limit|thread|concurren/i.test(requirements)) {
        expect(assessment.unrequestedOperationalScope).toBe(false);
    }
    expect(assessment.contradictoryConflictExample).toBe(false);
    expect(assessment.signOff).toBe(false);
}

function assessGeneratedExercise(persistedExercise: { problemStatement?: string }, repositories: any, status: any) {
    const statement = persistedExercise.problemStatement ?? '';
    const statementAssessment = assessProblemStatement(statement);
    const boundTestNames = statementAssessment.taskBindings.flatMap((binding) =>
        binding
            .split(',')
            .map((name) => name.trim())
            .filter(Boolean),
    );
    const selected = repositories.selectedContent ?? {};
    const allSolution = Object.values(selected.solution ?? {}).join('\n');
    const allTemplate = Object.values(selected.template ?? {}).join('\n');
    const allTests = Object.values(selected.tests ?? {}).join('\n');
    const allGeneratedSources = `${allSolution}\n${allTemplate}\n${allTests}`;
    const workedCommandStrings = new Set([...statement.matchAll(/"([LRM]{3,})"/g)].map((match) => match[1]));
    const testedCommandStrings = new Set([...allTests.matchAll(/"([LRM]{3,})"/g)].map((match) => match[1]));
    const mechanicsLeaks = /\b(agent|sandbox|verifier|template repository|solution repository|test repository|hidden tests|test runner)\b/i.test(statement);
    const duplicateTasksSection = (statement.match(/^##\s+Tasks\b/gim) ?? []).length > 1;
    const reviewNoteMentioned = /review note/i.test(status.terminal?.message ?? '');
    const visibleStatementMentionsRawTestNamesOutsideTaskBindings = /\btest[A-Z]\w+\b/.test(stripTaskBindings(statement));
    // The fields below (diagnostic*) are crude regex proxies over test source text (e.g. "does the test file contain the substring 'boundary'?"). They cannot prove genuine
    // assertion quality, edge-case coverage, or failure-message quality — the differential oracle and spec-fidelity critic are the actual acceptance authorities for that (see
    // AuthoritativeVerificationService / SpecFidelityCriticService). Keep them out of assertGeneratedExerciseQuality's hard gates; they are recorded in the JSON report purely as
    // non-blocking diagnostic signal for a human skimming a run.
    const diagnosticTestsHaveAssertions = /\bassert(That|Equals|Throws|True|False|All)\s*\(/.test(allTests);
    const diagnosticTestsHaveMessages = /,\s*"[^"]{8,}"\s*\)/.test(allTests);
    const diagnosticTestsHaveEdgeCases = /assertThrows|boundary|invalid|empty|null|edge/i.test(allTests);
    return {
        statementAssessment,
        accepted: status.terminal?.verdict?.mechanicallyVerified ?? false,
        completionStatus: status.terminal?.completionStatus,
        solutionPassed: status.terminal?.verdict?.solutionPassed,
        templateFailed: status.terminal?.verdict?.templateFailed,
        testCount: status.terminal?.verdict?.testCount,
        reviewNoteMentioned,
        duplicateTasksSection,
        duplicateHeadings: new Set(statementAssessment.headings).size !== statementAssessment.headings.length,
        copiedWorkedCommandStrings: [...workedCommandStrings].filter((command) => testedCommandStrings.has(command)),
        mechanicsLeaks,
        taskBindingsUnique: new Set(boundTestNames).size === boundTestNames.length,
        ambiguousOutputContract: /output may be returned[^.]*or printed|single composite object, separate values/i.test(statement),
        solutionHasUnsupportedOperation: allSolution.includes('UnsupportedOperationException'),
        templateHasTodoOrUnsupportedOperation: /TODO|UnsupportedOperationException|Not implemented/i.test(allTemplate),
        definesTrustedFrameworkPackage: /^\s*package\s+(?:de\.tum\.in\.(?:ase\.test|test\.api)(?:\.|;)|org\.junit(?:\.|;)|net\.bytebuddy(?:\.|;))/m.test(allGeneratedSources),
        diagnosticTestsHaveAssertions,
        diagnosticTestsHaveMessages,
        diagnosticTestsHaveEdgeCases,
        visibleStatementMentionsRawTestNamesOutsideTaskBindings,
    };
}

/**
 * Hard acceptance-style gates only. These check properties the server's own mechanical verifier and structural regexes can prove outright (bindings resolve, no duplicate
 * sections, no leaked implementation-detail vocabulary, no forbidden framework package, etc.) — never the diagnostic-only test-quality heuristics computed above.
 */
function assertGeneratedExerciseQuality(assessment: GeneratedExerciseAssessment) {
    expect(assessment.accepted).toBe(true);
    expect(assessment.solutionPassed).toBe(true);
    expect(assessment.templateFailed).toBe(true);
    expect(assessment.testCount).toBeGreaterThan(0);
    expect(assessment.statementAssessment.taskCount).toBeGreaterThan(0);
    expect(assessment.statementAssessment.publicApiDetails).toBe(true);
    expect(assessment.taskBindingsUnique).toBe(true);
    expect(assessment.duplicateHeadings).toBe(false);
    expect(assessment.copiedWorkedCommandStrings).toEqual([]);
    expect(assessment.ambiguousOutputContract).toBe(false);
    expect(assessment.solutionHasUnsupportedOperation).toBe(false);
    expect(assessment.definesTrustedFrameworkPackage).toBe(false);
    expect(assessment.statementAssessment.hasReplacementCharacter).toBe(false);
    expect(assessment.statementAssessment.hasAuthoringResidue).toBe(false);
    expect(assessment.mechanicsLeaks).toBe(false);
    expect(assessment.visibleStatementMentionsRawTestNamesOutsideTaskBindings).toBe(false);
}

// Removes the two places a statement is SUPPOSED to name a test: the [task] bindings and the PlantUML testsColor(...) links. Whatever survives is a genuine prose leak. Without
// the testsColor exclusion this reported a leak for every exercise whose diagram carries the interactive links the generator is required to emit — four of ten runs, all false.
function stripTaskBindings(statement: string) {
    return statement.replace(/\[task]\[[^\]]+\]\(.*\)\s*$/gm, '').replace(/testsColor\([^)]*\)/g, '');
}

function liveReportMetadata() {
    return {
        gitSha: safeShell('git rev-parse HEAD'),
        gitDiffHash: safeShell("git diff HEAD -- | sha256sum | cut -d' ' -f1"),
        draftPromptHash: fileHash(path.join(repoRoot, 'src/main/resources/prompts/hyperion/generate_draft_problem_statement_system.st')),
        liveModel: process.env.SPRING_AI_OPENAI_CHAT_MODEL,
        concurrentJobs,
        concurrentRunId,
        validatorVersion: 'artifact-integrity-v5',
    };
}

function fileHash(file: string) {
    return createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

function safeShell(command: string) {
    try {
        return execSync(command, { cwd: repoRoot, encoding: 'utf8' }).trim();
    } catch {
        return undefined;
    }
}

function serializeError(error: unknown) {
    if (error instanceof Error) {
        return { name: error.name, message: error.message, stack: error.stack };
    }
    return { message: String(error) };
}

async function ensureReportDir() {
    await fs.promises.mkdir(runReportDir, { recursive: true });
}

async function writeScenarioReport(id: string, report: Record<string, unknown>) {
    await fs.promises.writeFile(path.join(runReportDir, `${id}.json`), JSON.stringify(report, null, 2));
}
