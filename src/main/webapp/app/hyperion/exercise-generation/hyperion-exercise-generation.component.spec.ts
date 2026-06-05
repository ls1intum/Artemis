import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { HyperionExerciseGenerationComponent } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.component';
import { HyperionExerciseGenerationService } from 'app/hyperion/services/hyperion-exercise-generation.service';
import { ExerciseGenerationEvent, HyperionExerciseGenerationWebsocketService } from 'app/hyperion/services/hyperion-exercise-generation-websocket.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

// The diff computation is exercised by its own tests; here we only assert the modal is opened with the fetched template/solution contents.
vi.mock('app/programming/shared/utils/diff.utils', async (importOriginal) => ({
    ...(await importOriginal<typeof import('app/programming/shared/utils/diff.utils')>()),
    processRepositoryDiff: vi.fn(() => Promise.resolve({ entries: [] })),
}));

describe('HyperionExerciseGenerationComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<HyperionExerciseGenerationComponent>;
    let component: HyperionExerciseGenerationComponent;
    let generationService: { generateExercise: ReturnType<typeof vi.fn>; cancel: ReturnType<typeof vi.fn>; getStatus: ReturnType<typeof vi.fn> };
    let websocketService: { subscribeToJob: ReturnType<typeof vi.fn>; unsubscribeFromJob: ReturnType<typeof vi.fn> };
    let alertService: { success: ReturnType<typeof vi.fn>; info: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };
    let programmingExerciseService: { getTemplateRepositoryTestFilesWithContent: ReturnType<typeof vi.fn>; getSolutionRepositoryTestFilesWithContent: ReturnType<typeof vi.fn> };
    let dialogService: { open: ReturnType<typeof vi.fn> };
    let jobStream: Subject<ExerciseGenerationEvent>;

    function create(): void {
        fixture = TestBed.createComponent(HyperionExerciseGenerationComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exerciseId', 42);
        fixture.detectChanges();
    }

    beforeEach(() => {
        jobStream = new Subject<ExerciseGenerationEvent>();
        // By default there is no run to resume.
        generationService = { generateExercise: vi.fn(), cancel: vi.fn(), getStatus: vi.fn().mockReturnValue(of(undefined)) };
        websocketService = { subscribeToJob: vi.fn().mockReturnValue(jobStream.asObservable()), unsubscribeFromJob: vi.fn() };
        alertService = { success: vi.fn(), info: vi.fn(), error: vi.fn() };
        programmingExerciseService = { getTemplateRepositoryTestFilesWithContent: vi.fn(), getSolutionRepositoryTestFilesWithContent: vi.fn() };
        dialogService = { open: vi.fn() };

        TestBed.configureTestingModule({
            imports: [HyperionExerciseGenerationComponent],
            providers: [
                { provide: HyperionExerciseGenerationService, useValue: generationService },
                { provide: HyperionExerciseGenerationWebsocketService, useValue: websocketService },
                { provide: AlertService, useValue: alertService },
                { provide: ProgrammingExerciseService, useValue: programmingExerciseService },
                { provide: DialogService, useValue: dialogService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        create();
    });

    it('starts a generation run, streams progress and reports success', () => {
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-1' }));
        const completed: boolean[] = [];
        component.generationCompleted.subscribe((v) => completed.push(v));

        component.generate();

        // There is no free-text brief on the detail page: a run always reuses the prior context (no prompt argument).
        expect(generationService.generateExercise).toHaveBeenCalledWith(42, undefined);
        expect(component.running()).toBe(true);
        expect(websocketService.subscribeToJob).toHaveBeenCalledWith('job-1');

        jobStream.next({ type: 'PROGRESS', message: 'Editing solution' });
        expect(component.progressEvents().map((e) => e.message)).toContain('Editing solution');

        jobStream.next({ type: 'DONE', completionStatus: 'SUCCESS', message: 'saved' });
        expect(component.running()).toBe(false);
        expect(component.succeeded()).toBe(true);
        expect(component.canRetry()).toBe(true);
        expect(alertService.success).toHaveBeenCalled();
        expect(completed).toEqual([true]);
    });

    it('reports a partial (not saved) outcome without a success alert', () => {
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-2' }));
        const completed: boolean[] = [];
        component.generationCompleted.subscribe((v) => completed.push(v));

        component.generate();
        jobStream.next({ type: 'DONE', completionStatus: 'PARTIAL', message: 'not verified' });

        expect(component.running()).toBe(false);
        expect(component.succeeded()).toBe(false);
        expect(alertService.success).not.toHaveBeenCalled();
        expect(completed).toEqual([false]);
    });

    it('reports a recovered (needs-review) outcome with an info alert and signals completion', () => {
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-nr' }));
        const completed: boolean[] = [];
        component.generationCompleted.subscribe((v) => completed.push(v));

        component.generate();
        jobStream.next({ type: 'DONE', completionStatus: 'NEEDS_REVIEW', message: 'draft saved, 3 issue(s) to review' });

        expect(component.running()).toBe(false);
        // A recovered draft is NOT a clean success, but it IS a non-error completion the editor should reload to surface the review panel.
        expect(component.succeeded()).toBe(false);
        expect(component.needsReview()).toBe(true);
        expect(alertService.success).not.toHaveBeenCalled();
        expect(alertService.info).toHaveBeenCalledWith('artemisApp.programmingExercise.generateExercise.needsReview');
        expect(completed).toEqual([true]);

        fixture.detectChanges();
        // The in-panel result banner must reflect that a draft was saved, not the "nothing was changed" partial message.
        const banner = (fixture.nativeElement as HTMLElement).querySelector('.generation-result')?.textContent ?? '';
        expect(banner).toContain('resultNeedsReview');
        expect(banner).not.toContain('resultPartial');
    });

    it('shows an error alert when starting fails', () => {
        generationService.generateExercise.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'already running' } })));
        component.generate();
        expect(component.running()).toBe(false);
        expect(alertService.error).toHaveBeenCalled();
    });

    it('cancels the running job and shows a cancelling state', () => {
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-3' }));
        generationService.cancel.mockReturnValue(new Subject<void>());
        component.generate();
        component.cancel();
        expect(generationService.cancel).toHaveBeenCalledWith(42, 'job-3');
        expect(component.cancelling()).toBe(true);
        expect(component.canCancel()).toBe(false);

        jobStream.next({ type: 'CANCELLED', message: 'cancelled' });
        expect(component.cancelling()).toBe(false);
        expect(component.running()).toBe(false);
    });

    it('resets running and alerts when the progress stream errors', () => {
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-4' }));
        component.generate();
        jobStream.error(new Error('socket closed'));
        expect(component.running()).toBe(false);
        expect(alertService.error).toHaveBeenCalled();
    });

    it('unsubscribes from the job channel on destroy to avoid leaks', () => {
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-5' }));
        component.generate();
        component.ngOnDestroy();
        expect(websocketService.unsubscribeFromJob).toHaveBeenCalledWith('job-5');
    });

    it('reattaches to a running job on load by replaying the transcript and resubscribing', () => {
        generationService.getStatus.mockReturnValue(of({ jobId: 'job-resume', running: true, events: [{ type: 'PROGRESS', message: 'earlier line' }] }));
        create();

        expect(component.jobId()).toBe('job-resume');
        expect(component.running()).toBe(true);
        expect(component.progressEvents().map((e) => e.message)).toEqual(['earlier line']);
        expect(websocketService.subscribeToJob).toHaveBeenCalledWith('job-resume');
    });

    it('exposes the structured verdict from the terminal event', () => {
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-v' }));
        component.generate();
        jobStream.next({
            type: 'DONE',
            completionStatus: 'PARTIAL',
            message: 'rejected',
            verdict: { accepted: false, solutionPassed: true, templateFailed: false, testCount: 5, reasons: ['template passes the tests'] },
        });
        expect(component.verdict()?.solutionPassed).toBe(true);
        expect(component.verdict()?.templateFailed).toBe(false);
        expect(component.verdict()?.reasons).toEqual(['template passes the tests']);
    });

    it('opens the diff modal with the template-vs-solution diff when reviewing changes', async () => {
        programmingExerciseService.getTemplateRepositoryTestFilesWithContent.mockReturnValue(of(new Map([['A.java', 'class A {}']])));
        programmingExerciseService.getSolutionRepositoryTestFilesWithContent.mockReturnValue(of(new Map([['A.java', 'class A { void x() {} }']])));
        component.reviewChanges();
        await new Promise((resolve) => setTimeout(resolve));
        expect(programmingExerciseService.getTemplateRepositoryTestFilesWithContent).toHaveBeenCalledWith(42);
        expect(programmingExerciseService.getSolutionRepositoryTestFilesWithContent).toHaveBeenCalledWith(42);
        expect(dialogService.open).toHaveBeenCalled();
        expect(component.diffLoading()).toBe(false);
    });

    it('shows the last outcome on load for a finished job without resubscribing', () => {
        generationService.getStatus.mockReturnValue(
            of({
                jobId: 'job-done',
                running: false,
                events: [
                    { type: 'PROGRESS', message: 'work' },
                    { type: 'DONE', completionStatus: 'SUCCESS', message: 'saved' },
                ],
            }),
        );
        create();

        expect(component.running()).toBe(false);
        expect(component.succeeded()).toBe(true);
        expect(component.finalEvent()?.type).toBe('DONE');
        expect(websocketService.subscribeToJob).not.toHaveBeenCalled();
    });

    function createWithAutoStart(): void {
        fixture = TestBed.createComponent(HyperionExerciseGenerationComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exerciseId', 42);
        fixture.componentRef.setInput('autoStart', true);
        fixture.detectChanges();
    }

    it('auto-starts a generation run on load when autoStart is set and no run exists', () => {
        generationService.getStatus.mockReturnValue(of(undefined));
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-auto' }));

        createWithAutoStart();

        expect(generationService.generateExercise).toHaveBeenCalledWith(42, undefined);
        expect(websocketService.subscribeToJob).toHaveBeenCalledWith('job-auto');
    });

    it('does not auto-start when a run is already in progress', () => {
        generationService.getStatus.mockReturnValue(of({ jobId: 'job-running', running: true, events: [] }));
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-auto' }));

        createWithAutoStart();

        expect(generationService.generateExercise).not.toHaveBeenCalled();
    });

    it('does not auto-start when a terminal outcome already exists', () => {
        generationService.getStatus.mockReturnValue(of({ jobId: 'job-final', running: false, events: [{ type: 'DONE', completionStatus: 'SUCCESS', message: 'saved' }] }));
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-auto' }));

        createWithAutoStart();

        expect(generationService.generateExercise).not.toHaveBeenCalled();
    });

    it('auto-starts after a failed status probe', () => {
        generationService.getStatus.mockReturnValue(throwError(() => new Error('probe failed')));
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-auto' }));

        createWithAutoStart();

        expect(generationService.generateExercise).toHaveBeenCalledWith(42, undefined);
    });

    it('derives a structured phase, attempt and files-changed view from the streamed lines', () => {
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-struct' }));
        component.generate();

        jobStream.next({ type: 'PROGRESS', message: 'Turn 1: write_file solution/A.java' });
        expect(component.progress().phase).toBe('authoring');
        expect(component.fileChangeCount()).toBe(1);
        expect(component.filesByRepo()).toEqual([{ repo: 'solution', files: [{ path: 'solution/A.java', repo: 'solution', action: 'create' }] }]);

        jobStream.next({ type: 'PROGRESS', message: 'Verifying the generated exercise (attempt 2 of 3)' });
        expect(component.progress().phase).toBe('verifying');
        expect(component.progress().attempt).toBe(2);
        expect(component.progress().attemptTotal).toBe(3);
    });

    it('keeps the raw agent log collapsed by default and toggles it', () => {
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-log' }));
        component.generate();
        jobStream.next({ type: 'PROGRESS', message: 'Turn 1: write_file solution/A.java' });

        expect(component.showLog()).toBe(false);
        component.showLog.set(true);
        expect(component.showLog()).toBe(true);
    });

    it('self-hides when idle and renders the branded card once a run is active or recent', () => {
        // No run started: the card is not a permanent fixture, so nothing renders.
        expect(component.hasActiveOrRecentRun()).toBe(false);
        expect((fixture.nativeElement as HTMLElement).querySelector('.hyperion-exercise-generation')).toBeNull();

        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-card' }));
        component.generate();
        fixture.detectChanges();

        expect(component.hasActiveOrRecentRun()).toBe(true);
        expect((fixture.nativeElement as HTMLElement).querySelector('.hyperion-exercise-generation')).not.toBeNull();
    });

    it('ticks the elapsed timer once per second while running, stops on terminal, and reseeds on a new run', () => {
        vi.useFakeTimers();
        try {
            generationService.generateExercise.mockReturnValue(of({ jobId: 'job-timer' }));
            component.generate();
            expect(component.elapsedSeconds()).toBe(0);
            vi.advanceTimersByTime(3000);
            expect(component.elapsedSeconds()).toBe(3);

            jobStream.next({ type: 'DONE', completionStatus: 'PARTIAL', message: 'x' });
            vi.advanceTimersByTime(3000);
            // The timer stops on the terminal event — no further ticks, and no second interval is left running.
            expect(component.elapsedSeconds()).toBe(3);

            component.generate();
            expect(component.elapsedSeconds()).toBe(0);
            vi.advanceTimersByTime(1000);
            expect(component.elapsedSeconds()).toBe(1);
        } finally {
            vi.useRealTimers();
        }
    });

    it('resets the elapsed timer, log toggle and transcript when a new run starts', () => {
        generationService.generateExercise.mockReturnValue(of({ jobId: 'job-reset' }));
        component.generate();
        jobStream.next({ type: 'PROGRESS', message: 'work' });
        component.showLog.set(true);
        jobStream.next({ type: 'DONE', completionStatus: 'PARTIAL', message: 'not verified' });

        component.generate();
        expect(component.progressEvents()).toEqual([]);
        expect(component.finalEvent()).toBeUndefined();
        expect(component.showLog()).toBe(false);
        expect(component.elapsedSeconds()).toBe(0);
    });
});
