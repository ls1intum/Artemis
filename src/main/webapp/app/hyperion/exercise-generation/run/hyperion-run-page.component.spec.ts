import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionJobRegistryService } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { HyperionRunPageComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-page.component';
import { HyperionGenerationEvent, HyperionGenerationStatus } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

const EXERCISE_ID = 42;
const COURSE_ID = 7;

function exercise(): ProgrammingExercise {
    const programmingExercise = { id: EXERCISE_ID, title: 'Bounded Stack' } as ProgrammingExercise;
    programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
    programmingExercise.projectType = ProjectType.PLAIN_MAVEN;
    programmingExercise.difficulty = DifficultyLevel.MEDIUM;
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

class MockGenerationService {
    response: Observable<HyperionGenerationStatus | null> = of(null);
    readonly getStatus = vi.fn(() => this.response);
    readonly cancel = vi.fn(() => of(undefined));
    readonly generate = vi.fn(() => of({ jobId: 'job-2' }));
    readonly subscribeToStream = vi.fn(() => EMPTY);
    readonly subscribeToExerciseState = vi.fn(() => EMPTY);
}

describe('HyperionRunPageComponent', () => {
    let service: MockGenerationService;
    let registry: { track: ReturnType<typeof vi.fn>; markSeen: ReturnType<typeof vi.fn> };
    let fixture: ComponentFixture<HyperionRunPageComponent>;

    beforeEach(() => {
        vi.useRealTimers();
        service = new MockGenerationService();
        registry = { track: vi.fn(), markSeen: vi.fn() };
        const routeSnapshot = {
            params: { exerciseId: String(EXERCISE_ID) },
            data: { programmingExercise: exercise() },
            pathFromRoot: [{ params: { courseId: String(COURSE_ID) } }, { params: { exerciseId: String(EXERCISE_ID) } }],
        };
        TestBed.configureTestingModule({
            imports: [HyperionRunPageComponent],
            providers: [
                provideRouter([]),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: HyperionExerciseGenerationService, useValue: service },
                { provide: HyperionJobRegistryService, useValue: registry },
                {
                    provide: ActivatedRoute,
                    useValue: { params: of(routeSnapshot.params), data: of(routeSnapshot.data), snapshot: routeSnapshot },
                },
            ],
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

    function stageState(stage: string): string | null | undefined {
        return fixture.nativeElement.querySelector(`[data-stage="${stage}"]`)?.getAttribute('data-state');
    }

    function testId(id: string): HTMLElement | null {
        return fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
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
        expect(outcome!.textContent).not.toContain('gradle exited with code 1');
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

    it('swaps Cancel for Run again once the run has ended', () => {
        render(status({ events: [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'ERROR', terminationReason: 'RUN_FAILED' })] }));

        expect(testId('hyperion-run-cancel')).toBeNull();
        expect(testId('hyperion-run-run-again')).not.toBeNull();
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

    it('invites a first run when the exercise has never generated anything', () => {
        render(null);

        expect(testId('hyperion-run-not-started')).not.toBeNull();
        expect(testId('hyperion-run-progress')).toBeNull();
    });

    it('names the exercise in translated terms, never as a raw enum', () => {
        render(status({ running: true, events: [event({ type: 'STARTED', phase: 'PREPARING' })] }));

        const meta = testId('hyperion-run-meta')!.textContent!;
        expect(meta).toContain('artemisApp.ProgrammingLanguage.JAVA');
        expect(meta).toContain('artemisApp.programmingExercise.projectTypes.PLAIN_MAVEN');
        expect(meta).toContain('artemisApp.DifficultyLevel.MEDIUM');
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

        expect(testId('hyperion-run-elapsed')!.textContent!.trim()).toBe('12:34');
    });

    it('shows no percentage and no progress bar anywhere on the page', () => {
        render(status({ running: true, events: [event({ type: 'STARTED', phase: 'PREPARING' })] }));

        expect(fixture.nativeElement.textContent).not.toContain('%');
        expect(fixture.nativeElement.querySelector('progress')).toBeNull();
        expect(fixture.nativeElement.querySelector('[role="progressbar"]')).toBeNull();
    });
});
