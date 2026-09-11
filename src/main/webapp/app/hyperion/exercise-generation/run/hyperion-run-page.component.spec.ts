import dayjs from 'dayjs/esm';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import { BehaviorSubject, EMPTY, Observable, Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import programmingExerciseEn from 'src/main/webapp/i18n/en/programmingExercise.json';
import programmingExerciseDe from 'src/main/webapp/i18n/de/programmingExercise.json';
import programmingLanguageEn from 'src/main/webapp/i18n/en/programmingLanguage.json';
import programmingLanguageDe from 'src/main/webapp/i18n/de/programmingLanguage.json';
import exerciseEn from 'src/main/webapp/i18n/en/exercise.json';
import exerciseDe from 'src/main/webapp/i18n/de/exercise.json';
import difficultyEn from 'src/main/webapp/i18n/en/difficultyLevel.json';
import difficultyDe from 'src/main/webapp/i18n/de/difficultyLevel.json';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationActivityFacade } from 'app/hyperion/exercise-generation/hyperion-generation-activity.facade';
import { HyperionJobRegistryService } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { HyperionRunPageComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-page.component';
import { HyperionGenerationEvent, HyperionGenerationStatus } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ExerciseGenerationLiveUsage } from 'app/openapi/model/exercise-generation-live-usage';
import { ExerciseGenerationUsage } from 'app/openapi/model/exercise-generation-usage';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

const EXERCISE_ID = 42;
const COURSE_ID = 7;

function exercise(): ProgrammingExercise {
    const programmingExercise = { id: EXERCISE_ID, title: 'Bounded Stack' } as ProgrammingExercise;
    programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
    programmingExercise.projectType = ProjectType.GRADLE_GRADLE;
    programmingExercise.difficulty = DifficultyLevel.MEDIUM;
    programmingExercise.releaseDate = dayjs().add(1, 'year');
    return programmingExercise;
}

let clock = 0;

function event(partial: Partial<HyperionGenerationEvent> & Pick<HyperionGenerationEvent, 'type'>): HyperionGenerationEvent {
    return { timestamp: new Date(Date.UTC(2026, 0, 1, 0, 0, clock++)).toISOString(), ...partial };
}

function status(partial: Partial<HyperionGenerationStatus>): HyperionGenerationStatus {
    return {
        jobId: 'job-1',
        running: false,
        events: [],
        fileChanges: [],
        revertAvailable: false,
        ownedByCaller: true,
        cancellable: false,
        accountingState: 'COMPLETE',
        // Retention is something the server has to assert; a test that does not say so has kept nothing.
        artifactsRetained: false,
        ...partial,
    };
}

function liveUsage(partial: Partial<ExerciseGenerationLiveUsage> = {}): ExerciseGenerationLiveUsage {
    return {
        inputTokens: 90_000,
        outputTokens: 10_000,
        cachedInputTokens: 40_000,
        billableTokens: 250_000,
        tokenBudget: 1_000_000,
        modelCalls: 12,
        estimatedCostEur: 0.42,
        estimatedCostComplete: true,
        ...partial,
    };
}

function sealedUsage(partial: Partial<ExerciseGenerationUsage> = {}): ExerciseGenerationUsage {
    return {
        modelCalls: 24,
        toolCalls: 60,
        agentTurns: 18,
        attempts: 2,
        inputTokens: 180_000,
        outputTokens: 20_000,
        cachedInputTokens: 80_000,
        cachedInputTokensComplete: true,
        estimatedCostEur: 0.84,
        estimatedCostEurComplete: true,
        models: ['gpt-5-mini'],
        providerRequestIds: ['req-1'],
        providerRequestIdsComplete: true,
        ...partial,
    };
}

class MockGenerationService {
    response: Observable<HyperionGenerationStatus | null> = of(null);
    readonly getStatus = vi.fn(() => this.response);
    readonly cancel = vi.fn(() => of(undefined));
    readonly generate = vi.fn(() => of({ jobId: 'job-2' }));
    readonly revertExerciseGeneration = vi.fn(() => of({ fullyReverted: true, revertedRepositories: ['template', 'solution', 'tests'], completedAt: '2026-09-08T12:00:00Z' }));
    readonly subscribeToStream = vi.fn(() => EMPTY);
    readonly subscribeToExerciseState = vi.fn(() => EMPTY);
}

describe('HyperionRunPageComponent', () => {
    let service: MockGenerationService;
    let registry: { track: ReturnType<typeof vi.fn>; markSeen: ReturnType<typeof vi.fn> };
    let fixture: ComponentFixture<HyperionRunPageComponent>;
    let routeParams: BehaviorSubject<{ exerciseId: string }>;
    let routeData: BehaviorSubject<{ programmingExercise: ProgrammingExercise }>;
    /** Everything the page asked the CDK announcer to read out, in order. */
    let announced: string[];
    let generationFeatureActive: boolean;

    beforeEach(() => {
        vi.useRealTimers();
        announced = [];
        generationFeatureActive = true;
        service = new MockGenerationService();
        registry = { track: vi.fn(), markSeen: vi.fn() };
        const routeSnapshot = {
            params: { exerciseId: String(EXERCISE_ID) },
            data: { programmingExercise: exercise() },
            pathFromRoot: [{ params: { courseId: String(COURSE_ID) } }, { params: { exerciseId: String(EXERCISE_ID) } }],
        };
        routeParams = new BehaviorSubject(routeSnapshot.params);
        routeData = new BehaviorSubject(routeSnapshot.data);
        TestBed.configureTestingModule({
            imports: [HyperionRunPageComponent],
            providers: [
                provideRouter([]),
                { provide: ProfileService, useValue: { isModuleFeatureActive: () => generationFeatureActive } },
                provideHttpClient(),
                provideHttpClientTesting(),
                provideTranslateService({ lang: 'en' }),
                { provide: HyperionExerciseGenerationService, useValue: service },
                { provide: HyperionJobRegistryService, useValue: registry },
                {
                    provide: ActivatedRoute,
                    useValue: { params: routeParams, data: routeData, snapshot: routeSnapshot },
                },
            ],
        });
        vi.spyOn(TestBed.inject(LiveAnnouncer), 'announce').mockImplementation((message) => {
            announced.push(String(message));
            return Promise.resolve();
        });
    });

    afterEach(() => {
        fixture?.destroy();
        vi.restoreAllMocks();
    });

    function render(replayed: HyperionGenerationStatus | null): ComponentFixture<HyperionRunPageComponent> {
        service.response = of(replayed);
        fixture = TestBed.createComponent(HyperionRunPageComponent);
        fixture.detectChanges();
        return fixture;
    }

    it('reloads the saved title after generation changes the exercise', () => {
        render(status({ running: true }));
        const find = vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'find').mockReturnValue(of(new HttpResponse({ body: { ...exercise(), title: 'Roman Numerals' } })));
        const facade = fixture.debugElement.injector.get(HyperionGenerationActivityFacade);

        facade.generationCompleted.next({ jobId: 'job-1', liveExerciseChanged: true });
        fixture.detectChanges();

        expect(find).toHaveBeenCalledWith(EXERCISE_ID);
        expect(fixture.nativeElement.textContent).toContain('Roman Numerals');
    });

    it('requires confirmation before undo and refreshes the restored exercise afterwards', () => {
        render(status({ revertAvailable: true, revertMode: 'GENERATE', mode: 'GENERATE' }));
        const find = vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'find').mockReturnValue(of(new HttpResponse({ body: { ...exercise(), title: 'Restored draft' } })));
        clickButton('hyperion-run-undo');
        expect(service.revertExerciseGeneration).not.toHaveBeenCalled();
        clickButton('hyperion-run-undo-confirm');
        expect(service.revertExerciseGeneration).toHaveBeenCalledExactlyOnceWith(EXERCISE_ID);
        expect(find).toHaveBeenCalledWith(EXERCISE_ID);
        expect(fixture.nativeElement.textContent).toContain('Restored draft');
        expect(testId('hyperion-run-undone')).not.toBeNull();
        expect(testId('hyperion-run-undo')).toBeNull();
    });

    it('keeps the exercise unchanged when the undo confirmation is dismissed', () => {
        render(status({ revertAvailable: true, revertMode: 'GENERATE' }));
        clickButton('hyperion-run-undo');
        fixture.componentInstance['dismissRevert']();
        fixture.detectChanges();
        expect(service.revertExerciseGeneration).not.toHaveBeenCalled();
        expect(testId('hyperion-run-undo-confirm')).toBeNull();
    });

    it('keeps the start action but says why it is blocked after release or for a nonqualified exercise configuration', () => {
        render(null);
        expect(fixture.componentInstance['startAvailable']()).toBe(true);
        expect(fixture.componentInstance['startBlockedReason']()).toBeUndefined();
        expect(testId('hyperion-run-start')!.querySelector('button')!.getAttribute('aria-disabled')).toBeNull();

        routeData.next({ programmingExercise: { ...exercise(), releaseDate: dayjs().subtract(1, 'day') } });
        fixture.detectChanges();
        expect(fixture.componentInstance['startAvailable']()).toBe(true);
        expect(fixture.componentInstance['startBlockedReason']()).toBe('artemisApp.hyperion.generation.blocker.released');
        const startButton = testId('hyperion-run-start')!.querySelector('button')!;
        expect(startButton.getAttribute('aria-disabled')).toBe('true');
        expect(startButton.hasAttribute('disabled')).toBe(false);

        startButton.click();
        fixture.detectChanges();
        expect(fixture.componentInstance['startDialogVisible']()).toBe(false);
        expect(document.querySelector('[data-testid="hyperion-run-prompt"]')).toBeNull();

        routeData.next({ programmingExercise: { ...exercise(), staticCodeAnalysisEnabled: true } });
        fixture.detectChanges();
        expect(fixture.componentInstance['startBlockedReason']()).toBe('artemisApp.hyperion.generation.blocker.staticCodeAnalysis');
        fixture.componentInstance['openStartDialog']();
        fixture.detectChanges();
        expect(fixture.componentInstance['startDialogVisible']()).toBe(false);
        expect(service.generate).not.toHaveBeenCalled();
    });

    it('keeps Run again in place but blocked once the exercise no longer qualifies', () => {
        render(status({ events: [event({ type: 'ERROR', terminationReason: 'RUN_FAILED' })] }));
        routeData.next({ programmingExercise: { ...exercise(), releaseDate: dayjs().subtract(1, 'day') } });
        fixture.detectChanges();

        const runAgain = testId('hyperion-run-run-again')!.querySelector('button')!;
        expect(runAgain.getAttribute('aria-disabled')).toBe('true');
        runAgain.click();
        fixture.detectChanges();

        expect(fixture.componentInstance['startDialogVisible']()).toBe(false);
        expect(service.generate).not.toHaveBeenCalled();
    });

    it('does not offer generation at all when the feature is off for this deployment', () => {
        generationFeatureActive = false;
        render(null);

        expect(fixture.componentInstance['startAvailable']()).toBe(false);
        expect(testId('hyperion-run-start')).toBeNull();
    });

    it('does not show a previous exercise when its refresh finishes after route navigation', () => {
        render(status({ running: true }));
        const refresh = new Subject<HttpResponse<ProgrammingExercise>>();
        vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'find').mockReturnValue(refresh);
        fixture.debugElement.injector.get(HyperionGenerationActivityFacade).generationCompleted.next({ jobId: 'job-1', liveExerciseChanged: true });

        routeParams.next({ exerciseId: '43' });
        routeData.next({ programmingExercise: { ...exercise(), id: 43, title: 'New Exercise' } });
        fixture.detectChanges();
        refresh.next(new HttpResponse({ body: { ...exercise(), title: 'Previous Exercise' } }));
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('New Exercise');
        expect(fixture.nativeElement.textContent).not.toContain('Previous Exercise');
    });

    function stageState(stage: string): string | null | undefined {
        return fixture.nativeElement.querySelector(`[data-stage="${stage}"]`)?.getAttribute('data-state');
    }

    function testId(id: string): HTMLElement | null {
        return fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
    }

    function clickButton(id: string): void {
        const host = testId(id) ?? document.querySelector(`[data-testid="${id}"]`);
        expect(host).not.toBeNull();
        (host!.querySelector('button') as HTMLButtonElement).click();
        fixture.detectChanges();
    }

    function dialogElement(id: string): HTMLElement {
        return document.querySelector(`[data-testid="${id}"]`)!;
    }

    function enterPrompt(prompt: string): void {
        const input = dialogElement('hyperion-run-prompt') as HTMLTextAreaElement;
        input.value = prompt;
        input.dispatchEvent(new Event('input'));
        fixture.detectChanges();
    }

    function confirmPrompt(): void {
        dialogElement('hyperion-run-prompt-confirm').querySelector('button')!.click();
        fixture.detectChanges();
    }

    /** The value cell of one column of the header's facts rail, or `null` when the rail withholds that column. */
    function fact(key: string): HTMLElement | null {
        return fixture.nativeElement.querySelector(`[data-fact-value="${key}"]`);
    }

    it('rebuilds the ladder from a replayed status, so a reload lands on the same picture', () => {
        render(
            status({
                running: true,
                cancellable: true,
                events: [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'PROGRESS', phase: 'DESIGNING', message: 'Choosing a concept' })],
            }),
        );

        expect(testId('hyperion-run-progress')).not.toBeNull();
        expect(stageState('prepare')).toBe('complete');
        expect(stageState('design')).toBe('current');
        expect(stageState('save')).toBe('pending');
        expect(fixture.nativeElement.textContent).toContain('Choosing a concept');
    });

    it('marks a finished run as seen, so the navbar badge clears when it is opened', () => {
        render(status({ events: [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'DONE', phase: 'SAVING', completionStatus: 'SUCCESS' })] }));

        expect(registry.markSeen).toHaveBeenCalledWith('job-1');
    });

    it('reports a failure with its translated cause instead of the server prose', () => {
        render(
            status({
                events: [
                    event({ type: 'STARTED', phase: 'PREPARING' }),
                    event({ type: 'PROGRESS', phase: 'VERIFYING' }),
                    event({ type: 'ERROR', message: 'gradle exited with code 1', terminationReason: 'ENVIRONMENT_UNAVAILABLE' }),
                ],
            }),
        );

        const outcome = testId('hyperion-run-outcome');
        expect(outcome).not.toBeNull();
        expect(outcome!.getAttribute('data-severity')).toBe('error');
        expect(testId('hyperion-run-outcome-reason')!.textContent).toContain('artemisApp.hyperion.generation.terminationReason.ENVIRONMENT_UNAVAILABLE');
        expect(outcome!.textContent).toContain('artemisApp.hyperion.generation.outcome.failedTitle');
        // The English server sentence belongs behind the disclosure, never in the headline.
        expect(outcome!.querySelector('tum-ui-message')!.textContent).not.toContain('gradle exited with code 1');
        // The disclosure keeps its content in the DOM so `aria-controls` always resolves, but out of the a11y tree.
        const technical = testId('hyperion-run-outcome-technical')!;
        expect(technical.getAttribute('data-collapsed')).toBe('true');
        expect(technical.querySelector('[aria-hidden="true"]')).not.toBeNull();
        // The count travels onto the header, so collapsing the log hides no statement about the run.
        expect(technical.textContent).toContain('artemisApp.hyperion.generation.outcome.serverMessageCount');
    });

    it('promises the work was kept only when the server says a candidate survived', () => {
        const failure = [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'ERROR', terminationReason: 'AGENT_ERROR' })];

        render(status({ events: failure, artifactsRetained: true }));
        expect(testId('hyperion-run-outcome')!.textContent).toContain('artemisApp.hyperion.generation.outcome.retained');
        expect(testId('hyperion-run-nothing-retained')).toBeNull();
    });

    it('says nothing was kept when the run died before its work could be copied out', () => {
        // The observed incident: the sandbox was torn down while the artifacts were being extracted, so every
        // copy-out failed. Claiming the work is there sends the instructor looking for files that do not exist.
        render(
            status({
                events: [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'ERROR', terminationReason: 'AGENT_ERROR' })],
                artifactsRetained: false,
            }),
        );

        const outcome = testId('hyperion-run-outcome')!;
        expect(testId('hyperion-run-nothing-retained')).not.toBeNull();
        expect(outcome.textContent).toContain('artemisApp.hyperion.generation.outcome.nothingRetained');
        expect(outcome.textContent).not.toContain('artemisApp.hyperion.generation.outcome.retained"');
    });

    it('offers Cancel while the run is going and the caller owns it', () => {
        render(status({ running: true, cancellable: true, ownedByCaller: true, events: [event({ type: 'STARTED', phase: 'PREPARING' })] }));

        expect(testId('hyperion-run-cancel')).not.toBeNull();
        expect(testId('hyperion-run-run-again')).toBeNull();
    });

    it('hides Cancel from an instructor who did not start the run, and says why', () => {
        render(status({ running: true, cancellable: false, ownedByCaller: false, events: [event({ type: 'STARTED', phase: 'PREPARING' })] }));

        expect(testId('hyperion-run-cancel')).toBeNull();
        expect(testId('hyperion-run-other-instructor')).not.toBeNull();
    });

    it("reports another instructor's run as running even though its events are withheld", () => {
        render(status({ running: true, cancellable: false, ownedByCaller: false, events: [] }));

        expect(testId('hyperion-run-status')?.textContent).toContain('artemisApp.hyperion.generation.status.running');
    });

    it('swaps Cancel for Run again once the run has ended', () => {
        render(status({ events: [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'ERROR', terminationReason: 'RUN_FAILED' })] }));

        expect(testId('hyperion-run-cancel')).toBeNull();
        expect(testId('hyperion-run-run-again')).not.toBeNull();
    });

    it.each(['GENERATE', 'ADAPT'] as const)('requires explicit instructions before starting another %s run', (mode) => {
        render(status({ mode, events: [event({ type: 'ERROR', terminationReason: 'RUN_FAILED' })] }));
        testId('hyperion-run-run-again')!.querySelector('button')!.click();
        fixture.detectChanges();

        expect(service.generate).not.toHaveBeenCalled();
        enterPrompt('  \n ');
        expect(dialogElement('hyperion-run-prompt-confirm').querySelector('button')!.disabled).toBe(true);
        confirmPrompt();
        expect(service.generate).not.toHaveBeenCalled();

        enterPrompt('  Implement a bounded stack with generics.  ');
        confirmPrompt();
        expect(service.generate).toHaveBeenCalledExactlyOnceWith(EXERCISE_ID, { mode, prompt: 'Implement a bounded stack with generics.' });
    });

    it('can cancel a new run without submitting anything', () => {
        render(status({ events: [event({ type: 'ERROR', terminationReason: 'RUN_FAILED' })] }));
        testId('hyperion-run-run-again')!.querySelector('button')!.click();
        fixture.detectChanges();
        enterPrompt('Implement a bounded stack with generics.');
        dialogElement('hyperion-run-prompt-cancel').querySelector('button')!.click();
        fixture.detectChanges();

        expect(service.generate).not.toHaveBeenCalled();
        expect(document.querySelector('[data-testid="hyperion-run-prompt"]')).toBeNull();
    });

    it('keeps the entered brief when starting fails, so retry submits the same instruction', () => {
        render(status({ events: [event({ type: 'ERROR', terminationReason: 'RUN_FAILED' })] }));
        service.generate.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
        testId('hyperion-run-run-again')!.querySelector('button')!.click();
        fixture.detectChanges();
        enterPrompt('Implement a bounded stack with generics.');
        confirmPrompt();

        expect((dialogElement('hyperion-run-prompt') as HTMLTextAreaElement).value).toBe('Implement a bounded stack with generics.');
        confirmPrompt();
        expect(service.generate).toHaveBeenCalledTimes(2);
        expect(service.generate).toHaveBeenLastCalledWith(EXERCISE_ID, { mode: 'GENERATE', prompt: 'Implement a bounded stack with generics.' });
    });

    it('tracks a late start response without attaching its job to a different exercise', () => {
        render(status({ events: [event({ type: 'ERROR', terminationReason: 'RUN_FAILED' })] }));
        const started = new Subject<{ jobId: string }>();
        service.generate.mockReturnValueOnce(started);
        const facade = fixture.debugElement.injector.get(HyperionGenerationActivityFacade);
        const attach = vi.spyOn(facade, 'attachToJob');
        testId('hyperion-run-run-again')!.querySelector('button')!.click();
        fixture.detectChanges();
        enterPrompt('Implement a bounded stack with generics.');
        confirmPrompt();
        routeParams.next({ exerciseId: '99' });
        fixture.detectChanges();
        started.next({ jobId: 'late-job' });

        expect(registry.track).toHaveBeenCalledWith({ jobId: 'late-job', exerciseId: EXERCISE_ID, courseId: COURSE_ID, exerciseTitle: 'Bounded Stack', mode: 'GENERATE' });
        expect(attach).not.toHaveBeenCalled();
    });

    it('does not carry instructions or a pending dialog to another exercise', () => {
        render(status({ events: [event({ type: 'ERROR', terminationReason: 'RUN_FAILED' })] }));
        testId('hyperion-run-run-again')!.querySelector('button')!.click();
        fixture.detectChanges();
        enterPrompt('Implement a bounded stack with generics.');
        routeParams.next({ exerciseId: '99' });
        fixture.detectChanges();

        expect(document.querySelector('[data-testid="hyperion-run-prompt"]')).toBeNull();
        expect(fixture.componentInstance['startPrompt']()).toBe('');
        expect(service.generate).not.toHaveBeenCalled();
    });

    it('keeps elapsed time and stall announcements working after Run again', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-09-07T10:00:00Z'));
        render(status({ events: [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'ERROR', terminationReason: 'RUN_FAILED' })] }));
        service.response = of(status({ jobId: 'job-2', running: true, events: [{ type: 'STARTED', phase: 'PREPARING', timestamp: new Date().toISOString() }] }));

        testId('hyperion-run-run-again')!.querySelector('button')!.click();
        fixture.detectChanges();
        enterPrompt('Implement a bounded stack with generics.');
        confirmPrompt();
        expect(fact('elapsed')?.textContent?.trim()).toBe('0:00');
        expect(announced).not.toContain('artemisApp.hyperion.generation.run.stalledAnnouncement');

        vi.advanceTimersByTime(90_000);
        fixture.detectChanges();

        expect(fact('elapsed')?.textContent?.trim()).toBe('1:30');
        expect(announced).toContain('artemisApp.hyperion.generation.run.stalledAnnouncement');
    });

    it('offers a retry that asks the server again when the status could not be loaded', () => {
        service.response = throwError(() => new HttpErrorResponse({ status: 400 }));
        fixture = TestBed.createComponent(HyperionRunPageComponent);
        fixture.detectChanges();

        const banner = testId('hyperion-run-status-unavailable');
        expect(banner).not.toBeNull();
        expect(banner!.textContent).toContain('artemisApp.hyperion.generation.run.statusUnavailable');

        const callsBefore = service.getStatus.mock.calls.length;
        (testId('hyperion-run-status-retry')!.querySelector('button') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(service.getStatus.mock.calls.length).toBeGreaterThan(callsBefore);
    });

    it('calls an exercise that never generated anything "no run yet" rather than "status unavailable"', () => {
        render(null);

        // The two states share a condition - there is no job - but only one of them is a problem the instructor can act on.
        expect(testId('hyperion-run-status')!.textContent).toContain('artemisApp.hyperion.generation.status.notStarted');
        expect(testId('hyperion-run-status')!.textContent).not.toContain('status.unknown');
    });

    it('reports the status as unavailable only when the server could not be asked', () => {
        // A 4xx is not retried, so the facade gives up on the first response and the page has its answer synchronously.
        service.response = throwError(() => new HttpErrorResponse({ status: 400 }));
        fixture = TestBed.createComponent(HyperionRunPageComponent);
        fixture.detectChanges();

        expect(testId('hyperion-run-status')!.textContent).toContain('artemisApp.hyperion.generation.status.unknown');
    });

    it('invites a first run when the exercise has never generated anything', () => {
        render(null);

        expect(testId('hyperion-run-not-started')).not.toBeNull();
        expect(testId('hyperion-run-progress')).toBeNull();
    });

    it('gives every region a real heading rather than a div sized by an override', () => {
        render(status({ running: true, events: [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'PROGRESS', phase: 'DESIGNING' })] }));

        const title = fixture.nativeElement.querySelector('#hyperion-run-progress-title') as HTMLElement;
        expect(title.tagName).toBe('H2');
        expect(title.className).not.toContain('!');
        // The ladder is a region labelled by that heading, so it is reachable without reading the page in order.
        expect(fixture.nativeElement.querySelector('[aria-labelledby="hyperion-run-progress-title"]')).not.toBeNull();
        // The step counter is the header's action, so the counter and the title are one row rather than two claims.
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-step-counter"]')).not.toBeNull();
    });

    it.each([
        { language: 'en', catalogues: [programmingExerciseEn, programmingLanguageEn, exerciseEn, difficultyEn], expected: 'Java · Gradle · Medium' },
        { language: 'de', catalogues: [programmingExerciseDe, programmingLanguageDe, exerciseDe, difficultyDe], expected: 'Java · Gradle · Mittel' },
    ])('renders localized metadata from the real $language catalogue', ({ language, catalogues, expected }) => {
        const translate = TestBed.inject(TranslateService);
        for (const catalogue of catalogues) {
            translate.setTranslation(language, catalogue, true);
        }
        translate.use(language);
        render(status({ running: true, events: [event({ type: 'STARTED', phase: 'PREPARING' })] }));

        expect(testId('hyperion-run-meta')!.textContent!.replace(/\s+/g, ' ').trim()).toBe(expected);
        for (const projectType of Object.values(ProjectType)) {
            const key = `artemisApp.programmingExercise.projectTypes.${projectType}`;
            expect(translate.instant(key)).not.toBe(key);
        }
    });

    it('groups the files written so far by repository and calls out the one being written now', () => {
        render(
            status({
                running: true,
                events: [event({ type: 'STARTED', phase: 'PREPARING' })],
                fileChanges: [
                    { type: 'FILE_CHANGE', path: 'tests/src/test/java/StackTest.java', repo: 'tests', action: 'write', turn: 1, timestamp: '2026-01-01T00:00:01Z' },
                    { type: 'FILE_CHANGE', path: 'solution/src/main/java/Stack.java', repo: 'solution', action: 'write', turn: 2, timestamp: '2026-01-01T00:00:02Z' },
                ],
            }),
        );

        const groups = [...fixture.nativeElement.querySelectorAll('[data-repo]')].map((group: Element) => group.getAttribute('data-repo'));
        // Solution first, then template, tests, other — the same order every surface uses.
        expect(groups).toEqual(['solution', 'tests']);
        const solution = fixture.nativeElement.querySelector('[data-repo="solution"]') as HTMLElement;
        // The path is split so the file name is never the part CSS clips away.
        expect(solution.textContent).toContain('Stack.java');
        expect(solution.textContent).toContain('artemisApp.hyperion.generation.artifacts.writingNow');
        expect(fixture.nativeElement.querySelector('[data-repo="tests"]')!.textContent).not.toContain('artemisApp.hyperion.generation.artifacts.writingNow');
    });

    it('reports how long a finished run took, not how long ago it was started', () => {
        render(
            status({
                events: [
                    { type: 'STARTED', phase: 'PREPARING', timestamp: '2026-01-01T10:00:00Z' },
                    { type: 'DONE', phase: 'SAVING', completionStatus: 'SUCCESS', timestamp: '2026-01-01T10:12:34Z' },
                ],
            }),
        );

        expect(fact('elapsed')!.textContent!.trim()).toBe('12:34');
    });

    it('never estimates how far along the run is: no percentage, no bar, no bar role', () => {
        // The agent's remaining work is not knowable, so the ladder reports a stage and a clock. The only bar on this
        // page is the token budget, which measures a real quantity against a real ceiling - and this run has neither.
        render(status({ running: true, events: [event({ type: 'STARTED', phase: 'PREPARING' })] }));

        expect(fixture.nativeElement.textContent).not.toContain('%');
        expect(fixture.nativeElement.querySelector('progress')).toBeNull();
        expect(fixture.nativeElement.querySelector('[role="progressbar"]')).toBeNull();
        expect(testId('hyperion-run-usage')).toBeNull();
    });

    it('reports how long each stage took, and keeps a clock on the one still running', () => {
        render(
            status({
                running: true,
                events: [
                    { type: 'STARTED', phase: 'PREPARING', timestamp: '2026-01-01T10:00:00Z' },
                    { type: 'PROGRESS', phase: 'DESIGNING', timestamp: '2026-01-01T10:01:30Z' },
                ],
            }),
        );

        const prepare = fixture.nativeElement.querySelector('[data-stage="prepare"] [data-testid="hyperion-run-stage-time"]') as HTMLElement;
        expect(prepare.textContent).toContain('artemisApp.hyperion.generation.stage.took');
        expect(prepare.getAttribute('data-live')).toBe('false');

        const design = fixture.nativeElement.querySelector('[data-stage="design"] [data-testid="hyperion-run-stage-time"]') as HTMLElement;
        expect(design.textContent).toContain('artemisApp.hyperion.generation.stage.runningFor');
        expect(design.getAttribute('data-live')).toBe('true');
        // A stage nobody has reached has no time to report.
        expect(fixture.nativeElement.querySelector('[data-stage="save"] [data-testid="hyperion-run-stage-time"]')).toBeNull();
    });

    describe('spend', () => {
        it('meters the newest streamed snapshot against the run budget while it is going', () => {
            render(
                status({
                    running: true,
                    accountingState: 'PENDING',
                    usage: sealedUsage(),
                    events: [
                        event({ type: 'STARTED', phase: 'PREPARING', liveUsage: liveUsage({ billableTokens: 10_000, modelCalls: 1 }) }),
                        event({ type: 'PROGRESS', phase: 'DESIGNING', liveUsage: liveUsage() }),
                    ],
                }),
            );

            const panel = testId('hyperion-run-usage')!;
            expect(panel.getAttribute('data-accounting')).toBe('PENDING');
            // The newest snapshot wins over both the older one and the status usage.
            expect(testId('hyperion-run-usage-budget')!.getAttribute('data-percent')).toBe('25');
            expect(testId('hyperion-run-usage-budget-used')!.textContent).toContain('250,000');
            expect(testId('hyperion-run-usage-budget-value')!.textContent).toContain('1,000,000');
            // Every threshold the bar's colour crosses also gets a word, so the colour is never the only signal.
            expect(testId('hyperion-run-usage-budget-level')!.textContent).toContain('artemisApp.hyperion.generation.usage.budgetWithin');
            // The bar's own reading is otherwise a bare "25 percent": the unit, the ceiling and the threshold word are
            // all in the text beside it, which a screen reader on the meter never reaches.
            const meter = fixture.nativeElement.querySelector('[role="progressbar"]') as HTMLElement;
            expect(meter.getAttribute('aria-valuetext')).toContain('artemisApp.hyperion.generation.usage.budgetValueText');
            expect(meter.getAttribute('aria-label')).toContain('artemisApp.hyperion.generation.usage.budgetAriaLabel');
            expect(testId('hyperion-run-usage-cost-amount')!.textContent!.trim()).toBe('€0.42');
            // A running run's figures are a running total, and the panel says so rather than leaving it to be inferred.
            expect(testId('hyperion-run-usage-accounting')!.textContent).toContain('artemisApp.hyperion.generation.usage.state.pending');
            expect(testId('hyperion-run-usage-cost-caption')!.textContent).toContain('artemisApp.hyperion.generation.usage.costSoFar');
            expect(panel.querySelector('[data-figure="input"]')!.textContent).toContain('90,000');
        });

        it('falls back to the status usage on reconnect, so a reload is not blank until the next event', () => {
            // The transcript that came back carries no snapshot; the status endpoint reports the accumulator instead.
            render(status({ running: true, accountingState: 'PENDING', usage: sealedUsage(), events: [event({ type: 'STARTED', phase: 'PREPARING' })] }));

            const panel = testId('hyperion-run-usage')!;
            expect(panel.querySelector('[data-figure="input"]')!.textContent).toContain('180,000');
            // The sealed shape carries no billable figure, so no proportion is drawn from it.
            expect(testId('hyperion-run-usage-budget')).toBeNull();
            expect(testId('hyperion-run-usage-no-budget')).toBeNull();
        });

        it('seals to the status total once the run has ended', () => {
            render(
                status({
                    accountingState: 'COMPLETE',
                    usage: sealedUsage(),
                    events: [event({ type: 'STARTED', phase: 'PREPARING', liveUsage: liveUsage() }), event({ type: 'DONE', phase: 'SAVING', completionStatus: 'SUCCESS' })],
                }),
            );

            const panel = testId('hyperion-run-usage')!;
            expect(panel.getAttribute('data-accounting')).toBe('COMPLETE');
            expect(testId('hyperion-run-usage-cost-amount')!.textContent!.trim()).toBe('€0.84');
            expect(panel.querySelector('[data-figure="input"]')!.textContent).toContain('180,000');
            expect(panel.querySelector('[data-figure="attempts"]')!.textContent).toContain('2');
            // The model list is diagnostic, so it lives in the technical log rather than on the surface reporting spend.
            expect(testId('hyperion-run-usage-models')).toBeNull();
            expect(testId('hyperion-run-outcome-models')!.textContent).toContain('gpt-5-mini');
        });

        it('shows an instructor watching someone else’s run no spend figures at all', () => {
            // The server withholds them, so anything rendered here could only be an empty or zeroed panel.
            render(
                status({
                    running: true,
                    ownedByCaller: false,
                    cancellable: false,
                    accountingState: 'INCOMPLETE',
                    events: [event({ type: 'PROGRESS', phase: 'DESIGNING', liveUsage: liveUsage() })],
                }),
            );

            expect(testId('hyperion-run-usage')).toBeNull();
        });

        it('says a run is not priced instead of showing it as free', () => {
            render(
                status({
                    running: true,
                    accountingState: 'PENDING',
                    events: [event({ type: 'PROGRESS', phase: 'DESIGNING', liveUsage: liveUsage({ estimatedCostEur: undefined, estimatedCostComplete: false }) })],
                }),
            );

            const cost = testId('hyperion-run-usage-cost')!;
            expect(cost.getAttribute('data-cost')).toBe('notPriced');
            // The hero slot holds the unit that was actually measured, not the word for the one that was not.
            expect(cost.getAttribute('data-hero')).toBe('tokens');
            expect(testId('hyperion-run-usage-cost-amount')!.textContent!.trim()).toBe('250,000');
            expect(testId('hyperion-run-usage-cost-unit')!.textContent).toContain('artemisApp.hyperion.generation.usage.billableTokens');
            // The currency amount is demoted to a labelled sentence rather than deleted, and never shown as a zero.
            expect(testId('hyperion-run-usage-cost-caption')!.textContent).toContain('artemisApp.hyperion.generation.usage.notPricedHint');
            expect(cost.textContent).not.toContain('0.00');
            expect(cost.textContent).not.toContain('€');
        });

        it('renders spend without a proportion when the deployment configured no token ceiling', () => {
            render(
                status({
                    running: true,
                    accountingState: 'PENDING',
                    events: [event({ type: 'PROGRESS', phase: 'DESIGNING', liveUsage: liveUsage({ tokenBudget: 0 }) })],
                }),
            );

            expect(testId('hyperion-run-usage-budget')).toBeNull();
            expect(fixture.nativeElement.querySelector('[role="progressbar"]')).toBeNull();
            expect(testId('hyperion-run-usage-no-budget')!.textContent).toContain('250,000');
        });

        it('gives every threshold the meter crosses a word, so the colour is never the only signal', () => {
            // A bar that turns amber and then red without saying so states its severity in hue alone (WCAG 1.4.1).
            const cases: readonly [number, string, string][] = [
                [250_000, 'artemisApp.hyperion.generation.usage.budgetWithin', 'within'],
                [800_000, 'artemisApp.hyperion.generation.usage.budgetNear', 'near'],
                [1_200_000, 'artemisApp.hyperion.generation.usage.budgetOver', 'over'],
            ];
            for (const [billableTokens, levelKey, level] of cases) {
                render(
                    status({
                        running: true,
                        accountingState: 'PENDING',
                        events: [event({ type: 'PROGRESS', phase: 'DESIGNING', liveUsage: liveUsage({ billableTokens }) })],
                    }),
                );

                expect(testId('hyperion-run-usage-budget')!.getAttribute('data-level')).toBe(level);
                expect(testId('hyperion-run-usage-budget-level')!.textContent).toContain(levelKey);
                // The ceiling is stated beside the share rather than welded into one unlabelled string with it.
                expect(testId('hyperion-run-usage-budget-value')!.textContent).toContain('1,000,000');
            }
        });

        it('reports a run past its ceiling as over budget rather than capping the meter at full', () => {
            render(
                status({
                    running: true,
                    accountingState: 'PENDING',
                    events: [event({ type: 'PROGRESS', phase: 'DESIGNING', liveUsage: liveUsage({ billableTokens: 1_200_000 }) })],
                }),
            );

            expect(testId('hyperion-run-usage-budget')!.getAttribute('data-percent')).toBe('120');
            expect(testId('hyperion-run-usage-budget-used')!.textContent).toContain('1,200,000');
        });

        it('says an audit that could not be closed is a lower bound, not a zero', () => {
            render(
                status({
                    accountingState: 'INCOMPLETE',
                    usage: sealedUsage({ cachedInputTokensComplete: false, estimatedCostEurComplete: false }),
                    events: [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'ERROR', terminationReason: 'AGENT_ERROR' })],
                }),
            );

            expect(testId('hyperion-run-usage')!.getAttribute('data-accounting')).toBe('INCOMPLETE');
            expect(testId('hyperion-run-usage-incomplete')!.textContent).toContain('artemisApp.hyperion.generation.usage.incompleteHint');
            expect(testId('hyperion-run-usage-cost')!.getAttribute('data-cost')).toBe('lowerBound');
            expect(testId('hyperion-run-usage-cost')!.textContent).toContain('artemisApp.hyperion.generation.usage.atLeast');
            // The cached share was not reported for every response, so it is a floor too.
            expect(testId('hyperion-run-usage-figures')!.querySelector('[data-figure="cached"]')!.textContent).toContain('artemisApp.hyperion.generation.usage.atLeast');
        });

        it('shows no panel at all before the run has been charged for anything', () => {
            render(status({ running: true, accountingState: 'PENDING', events: [event({ type: 'STARTED', phase: 'PREPARING' })] }));

            expect(testId('hyperion-run-usage')).toBeNull();
        });

        it('stamps a sealed total with the moment it stopped moving', () => {
            // A number read a week after the run finished must not be mistaken for one that is still being counted.
            render(
                status({
                    accountingState: 'COMPLETE',
                    usage: sealedUsage(),
                    events: [
                        { type: 'STARTED', phase: 'PREPARING', timestamp: '2026-01-01T10:00:00Z' },
                        { type: 'DONE', phase: 'SAVING', completionStatus: 'SUCCESS', timestamp: '2026-01-01T10:12:34Z' },
                    ],
                }),
            );

            expect(testId('hyperion-run-usage-sealed-at')!.textContent).toContain('artemisApp.hyperion.generation.usage.sealedAt');
        });

        it('does not stamp a running total, which has not stopped moving', () => {
            render(status({ running: true, accountingState: 'PENDING', events: [event({ type: 'PROGRESS', phase: 'DESIGNING', liveUsage: liveUsage() })] }));

            expect(testId('hyperion-run-usage-sealed-at')).toBeNull();
        });
    });

    describe('facts rail', () => {
        it('carries the spend above the fold while the run is still going', () => {
            // Cost is made prominent by position and persistence, not by size: the answer keeps the largest element.
            render(status({ running: true, accountingState: 'PENDING', events: [event({ type: 'PROGRESS', phase: 'DESIGNING', liveUsage: liveUsage() })] }));

            expect(fact('spend')!.textContent!.trim()).toBe('€0.42');
        });

        it('withholds the Spend column entirely from an instructor who does not own the run', () => {
            // Not zeroed and not empty: the server withholds the figures, and a zeroed column reads as a free run.
            render(
                status({
                    running: true,
                    ownedByCaller: false,
                    accountingState: 'PENDING',
                    events: [event({ type: 'PROGRESS', phase: 'DESIGNING', liveUsage: liveUsage() })],
                }),
            );

            expect(fact('spend')).toBeNull();
            expect(testId('hyperion-run-facts')).not.toBeNull();
        });

        it('leads the Spend column with tokens when the deployment prices nothing', () => {
            render(
                status({
                    running: true,
                    accountingState: 'PENDING',
                    events: [event({ type: 'PROGRESS', phase: 'DESIGNING', liveUsage: liveUsage({ estimatedCostEur: undefined, estimatedCostComplete: false }) })],
                }),
            );

            expect(fact('spend')!.textContent!.trim()).toBe('250,000');
            const column = fixture.nativeElement.querySelector('[data-fact="spend"]') as HTMLElement;
            expect(column.textContent).toContain('artemisApp.hyperion.generation.run.costNotPriced');
            expect(column.textContent).not.toContain('€');
        });

        it('reports the position in the five stages, and never a completion percentage', () => {
            render(
                status({
                    running: true,
                    events: [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'PROGRESS', phase: 'DESIGNING' })],
                }),
            );

            expect(fact('step')).not.toBeNull();
            expect(testId('hyperion-run-step-counter')).not.toBeNull();
            expect(fixture.nativeElement.textContent).not.toContain('%');
        });

        it('omits a column it has nothing to say in, rather than showing it blank', () => {
            render(status({ running: true, events: [event({ type: 'STARTED', phase: 'PREPARING' })] }));

            // No file has been written, so there is no file count - not a zero.
            expect(fact('files')).toBeNull();
        });
    });

    describe('status unavailable', () => {
        function renderStale(): void {
            // A replayed transcript, then a status check that stopped succeeding: the picture on screen is still true,
            // it is simply no longer current. The facade reaches this after repeated background failures; the page's
            // job is what it renders once it is there, so the flag is set directly rather than through a timer dance.
            render(
                status({
                    running: true,
                    events: [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'PROGRESS', phase: 'DESIGNING' })],
                }),
            );
            fixture.debugElement.injector.get(HyperionGenerationActivityFacade).statusLoadFailed.set(true);
            fixture.detectChanges();
        }

        it('keeps the last known ladder on screen instead of blanking the page', () => {
            renderStale();

            expect(testId('hyperion-run-status-unavailable')).not.toBeNull();
            // Stale beats blank: erasing a ladder that is still correct tells the reader less than leaving it up.
            expect(testId('hyperion-run-progress')).not.toBeNull();
            expect(stageState('design')).toBe('current');
        });

        it('says when the picture was last true, and that the run itself has not stopped', () => {
            renderStale();

            const banner = testId('hyperion-run-status-unavailable')!;
            expect(testId('hyperion-run-last-update')).not.toBeNull();
            expect(banner.textContent).toContain('artemisApp.hyperion.generation.run.stillRunningOnServer');
        });
    });

    describe('the empty state', () => {
        it('offers the action that starts a run rather than directions to it', () => {
            render(null);

            expect(testId('hyperion-run-not-started')).not.toBeNull();
            expect(testId('hyperion-run-start')).not.toBeNull();
        });

        it('requires a brief when no prior run is retained', () => {
            render(null);

            (testId('hyperion-run-start')!.querySelector('button') as HTMLButtonElement).click();

            fixture.detectChanges();
            expect(service.generate).not.toHaveBeenCalled();
            enterPrompt('Implement a bounded stack with generics.');
            confirmPrompt();

            expect(service.generate).toHaveBeenCalledExactlyOnceWith(EXERCISE_ID, { mode: 'GENERATE', prompt: 'Implement a bounded stack with generics.' });
        });
    });

    describe('announcements', () => {
        function announcements(): string[] {
            return announced;
        }

        /** A live event stamped now, so the page reads it as a run that is working rather than one that has stalled. */
        function fresh(partial: Partial<HyperionGenerationEvent> & Pick<HyperionGenerationEvent, 'type'>): HyperionGenerationEvent {
            return { timestamp: new Date().toISOString(), ...partial };
        }

        it('announces the rung the run is on, once, rather than every poll', () => {
            render(status({ running: true, events: [fresh({ type: 'STARTED', phase: 'PREPARING' }), fresh({ type: 'PROGRESS', phase: 'DESIGNING' })] }));
            fixture.detectChanges();

            expect(announcements()).toEqual(['artemisApp.hyperion.generation.run.stageAnnouncement']);
        });

        it('announces a stall, because a run that has gone quiet is the one thing the reader has to be told', () => {
            // These events are hours old, which is exactly the state the announcement exists for.
            render(status({ running: true, events: [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'PROGRESS', phase: 'DESIGNING' })] }));

            expect(announcements()).toEqual(['artemisApp.hyperion.generation.run.stalledAnnouncement']);
        });

        it('announces the verdict, because the instructor who started the run is not watching the page', () => {
            render(status({ events: [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'DONE', phase: 'SAVING', completionStatus: 'SUCCESS' })] }));

            expect(announcements()).toContain('artemisApp.hyperion.generation.outcome.savedTitle');
        });

        it('never announces the clock, the counters or a per-file event', () => {
            render(
                status({
                    running: true,
                    events: [fresh({ type: 'STARTED', phase: 'PREPARING' })],
                    fileChanges: [{ type: 'FILE_CHANGE', path: 'tests/StackTest.java', repo: 'tests', action: 'write', turn: 1, timestamp: new Date().toISOString() }],
                }),
            );
            fixture.detectChanges();

            // `role="timer"` has implicit `aria-live="off"` for exactly this reason: a value that updates once a second
            // is not a status message, and a page that reads its own clock aloud is unusable with a screen reader.
            expect(announcements()).toEqual(['artemisApp.hyperion.generation.run.stageAnnouncement']);
        });
    });

    describe('the terminal page', () => {
        function renderFinished(): void {
            render(
                status({
                    accountingState: 'COMPLETE',
                    usage: sealedUsage(),
                    events: [
                        { type: 'STARTED', phase: 'PREPARING', timestamp: '2026-01-01T10:00:00Z' },
                        { type: 'DONE', phase: 'SAVING', completionStatus: 'SUCCESS', timestamp: '2026-01-01T10:12:34Z' },
                    ],
                }),
            );
        }

        it('folds the ladder into a strip and leads with the verdict', () => {
            renderFinished();

            const strip = testId('hyperion-run-stage-strip')!;
            expect(strip.getAttribute('data-collapsed')).toBe('true');
            // The stages are still real information about what the run did; they are simply no longer the answer.
            expect(strip.querySelector('[data-testid="hyperion-run-progress"]')).not.toBeNull();
            expect(testId('hyperion-run-outcome')).not.toBeNull();
        });

        it('puts the verdict above the spend, not below it', () => {
            renderFinished();

            const outcome = testId('hyperion-run-outcome')!;
            const spend = testId('hyperion-run-usage')!;
            // `DOCUMENT_POSITION_FOLLOWING` means the spend comes after the verdict in the page.
            expect(outcome.compareDocumentPosition(spend) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        });
    });
});
