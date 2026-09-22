import { HyperionRunPageComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-page.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { effect, signal } from '@angular/core';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { NavigationEnd, Router, provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { TumUiConfirmationService } from '@tumaet/ui-angular';
import { MockComponent } from 'ng-mocks';
import { Subject, filter, firstValueFrom, of, take, throwError } from 'rxjs';
import { Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { HyperionJobEntry, HyperionJobRegistryService } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { ExerciseVariantGenerationService } from 'app/hyperion/services/exercise-variant-generation.service';
import { ExerciseVariantAiModalWizardComponent } from 'app/hyperion/variants/exercise-variant-ai-modal-wizard.component';
import { VariantJob } from 'app/openapi/model/variant-job';
import { HyperionActivityTrayComponent } from './hyperion-activity-tray.component';

const running: HyperionJobEntry = {
    jobId: 'a',
    courseId: 1,
    exerciseId: 2,
    exerciseTitle: 'Stack',
    mode: 'ADAPT',
    startedAt: '2026-09-21T12:00:00Z',
    status: 'running',
    cancellable: true,
    seen: false,
    phase: 'DESIGNING',
};
const quiz: VariantJob = { jobId: 'q', sourceExerciseTitle: 'Quiz', exerciseType: 'quiz', phase: 'TRANSFORMING' };

describe('HyperionActivityTrayComponent', () => {
    let fixture: ComponentFixture<HyperionActivityTrayComponent>;
    let entries: ReturnType<typeof signal<HyperionJobEntry[]>>;
    let jobs: ReturnType<typeof signal<VariantJob[]>>;
    let identity: ReturnType<typeof signal<{ login: string } | undefined>>;
    let variants: { jobs: typeof jobs; loadJobs: ReturnType<typeof vi.fn>; cancelJob: ReturnType<typeof vi.fn> };
    let registry: {
        entries: typeof entries;
        loadFailed: ReturnType<typeof signal<boolean>>;
        hasMoreHistory: ReturnType<typeof signal<boolean>>;
        loadMoreHistory: ReturnType<typeof vi.fn>;
        refresh: ReturnType<typeof vi.fn>;
        markSeen: ReturnType<typeof vi.fn>;
    };
    let registryFactory: Mock<() => typeof registry>;
    let generation: { cancel: ReturnType<typeof vi.fn> };

    const query = (id: string) => document.querySelector<HTMLElement>(`[data-testid="${id}"]`);
    const open = (authoringEnabled = true) => {
        fixture.componentRef.setInput('authoringEnabled', authoringEnabled);
        fixture.detectChanges();
        query('ai-activity-trigger')!.click();
        fixture.detectChanges();
    };

    beforeEach(async () => {
        localStorage.clear();
        entries = signal<HyperionJobEntry[]>([]);
        jobs = signal<VariantJob[]>([]);
        identity = signal<{ login: string } | undefined>({ login: 'editor' });
        registry = { entries, hasMoreHistory: signal(false), loadMoreHistory: vi.fn(), loadFailed: signal(false), refresh: vi.fn(), markSeen: vi.fn() };
        registryFactory = vi.fn(() => {
            // The real registry creates effects; retain that lifecycle boundary in this fixture.
            effect(() => identity());
            return registry;
        });
        variants = { jobs, loadJobs: vi.fn(() => of([])), cancelJob: vi.fn(() => of(undefined)) };
        generation = { cancel: vi.fn(() => of(undefined)) };
        await TestBed.configureTestingModule({
            imports: [HyperionActivityTrayComponent],
            providers: [
                provideRouter([{ path: '**', children: [] }]),
                provideLocationMocks(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useValue: { userIdentity: identity, hasAnyAuthorityDirect: () => true } },
                { provide: AlertService, useValue: { error: vi.fn() } },
                { provide: HyperionJobRegistryService, useFactory: () => registryFactory() },
                { provide: HyperionExerciseGenerationService, useValue: generation },
                { provide: ExerciseVariantGenerationService, useValue: variants },
            ],
        })
            .overrideComponent(HyperionActivityTrayComponent, {
                remove: { imports: [ExerciseVariantAiModalWizardComponent, HyperionRunPageComponent] },
                add: { imports: [MockComponent(ExerciseVariantAiModalWizardComponent), MockComponent(HyperionRunPageComponent)] },
            })
            .compileComponents();
        // The application bootstrap normally registers the browser-history listener.
        TestBed.inject(Router).setUpLocationChangeListener();
        fixture = TestBed.createComponent(HyperionActivityTrayComponent);
        fixture.detectChanges();
    });
    afterEach(() => {
        fixture.destroy();
        vi.restoreAllMocks();
    });

    it('renders no header control without jobs', () => expect(query('ai-activity-trigger')).toBeNull());

    it('shows one trigger with running AND attention states from both workflows', () => {
        entries.set([running]);
        jobs.set([{ ...quiz, phase: 'FAILED' }]);
        open();
        expect(document.querySelectorAll('[data-testid="ai-activity-trigger"]')).toHaveLength(1);
        expect(query('ai-activity-running')).not.toBeNull();
        expect(query('ai-activity-attention')).not.toBeNull();
        expect(document.querySelectorAll('[data-testid="ai-activity-entry"]')).toHaveLength(2);
        expect(query('ai-activity-steps')?.querySelector('[aria-current="step"]')?.textContent).toContain('stage.revise');
    });

    it('keeps authoring hidden when its module is off without hiding quiz activity', () => {
        fixture.componentRef.setInput('authoringEnabled', false);
        entries.set([running]);
        jobs.set([quiz]);
        open(false);
        expect(registryFactory).not.toHaveBeenCalled();
        expect(document.querySelectorAll('[data-testid="ai-activity-entry"]')).toHaveLength(1);
        expect(query('ai-activity-open')?.textContent).toContain('Quiz');
        expect(registry.refresh).not.toHaveBeenCalled();
    });

    it('keeps active work visible and does not offer dismissal', () => {
        entries.set([running]);
        open();
        expect(query('ai-activity-dismiss')).toBeNull();
        fixture.componentInstance['dismiss'](fixture.componentInstance['rows']()[0]);
        fixture.detectChanges();
        expect(query('ai-activity-entry')).not.toBeNull();
        expect(generation.cancel).not.toHaveBeenCalled();
    });

    it('hides the header entry after dismissing all completed work and remembers it after reload', () => {
        jobs.set([{ ...quiz, phase: 'FAILED' }]);
        open();
        query('ai-activity-dismiss')!.click();
        fixture.detectChanges();
        expect(query('ai-activity-trigger')).toBeNull();
        jobs.set([{ ...quiz, phase: 'COMPLETED' }]);
        fixture.detectChanges();
        expect(query('ai-activity-trigger')).toBeNull();
        fixture.destroy();
        fixture = TestBed.createComponent(HyperionActivityTrayComponent);
        fixture.detectChanges();
        expect(query('ai-activity-trigger')).toBeNull();
        jobs.set([{ ...quiz, jobId: 'new-job', phase: 'ANALYZING' }]);
        fixture.detectChanges();
        expect(query('ai-activity-trigger')).not.toBeNull();
        expect(variants.cancelJob).not.toHaveBeenCalled();
    });

    it('dismisses terminal warnings without deleting their recovery records', () => {
        entries.set([{ ...running, status: 'partial' }]);
        jobs.set([{ ...quiz, phase: 'CANCELLED', variantExerciseId: 9 }]);
        open();
        expect(document.querySelectorAll('[data-testid="ai-activity-recovery"]')).toHaveLength(2);
        expect(query('ai-activity-dismiss')).not.toBeNull();
        for (const row of fixture.componentInstance['rows']()) fixture.componentInstance['dismiss'](row);
        expect(fixture.componentInstance['visibleRows']()).toHaveLength(0);
        expect(entries()).toHaveLength(1);
        expect(jobs()).toHaveLength(1);
    });

    it('opens the exact authoring run in a URL-backed inspector and closes the tray', async () => {
        entries.set([{ ...running, status: 'saved' }]);
        open();
        const link = query('ai-activity-open')!;
        link.click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(TestBed.inject(Router).url).toContain('aiRun=authoring:2:a');
        expect(fixture.componentInstance['inspector']()).toEqual([{ key: 'editor:authoring:2:a', exerciseId: 2, runId: 'a' }]);
        expect(registry.markSeen).toHaveBeenCalledWith('a');
        expect(query('ai-activity-tray')).toBeNull();
    });

    it('opens quiz inspection in the URL without cancelling or navigating to the exercise', async () => {
        jobs.set([quiz]);
        open();
        query('ai-activity-open')!.click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(TestBed.inject(Router).url).toContain('aiRun=variant:q');
        expect(fixture.componentInstance['monitorJobId']()).toBe('q');
        expect(fixture.componentInstance['monitorVisible']()).toBe(true);
        expect(query('ai-activity-tray')).toBeNull();
        expect(variants.cancelJob).not.toHaveBeenCalled();
    });

    it('restores a directly linked inspector without a cached job and closes without navigating away', async () => {
        const router = TestBed.inject(Router);
        await router.navigateByUrl('/course-management/7/exercises?filter=mine&aiRun=variant:retained#list');
        fixture.detectChanges();
        expect(fixture.componentInstance['monitorVisible']()).toBe(true);
        expect(fixture.componentInstance['monitorJobId']()).toBe('retained');
        fixture.componentInstance['closeInspector'](false);
        await fixture.whenStable();
        expect(router.url).toBe('/course-management/7/exercises?filter=mine#list');
        expect(fixture.componentInstance['monitorVisible']()).toBe(false);
    });

    it('uses browser history to close a tray-opened inspector', async () => {
        const router = TestBed.inject(Router);
        await router.navigateByUrl('/course-management/7/exercises?filter=mine');
        jobs.set([quiz]);
        open();
        query('ai-activity-open')!.click();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(router.url).toContain('filter=mine');
        const location = TestBed.inject(Location);
        const back = vi.spyOn(location, 'back');
        const closed = firstValueFrom(
            router.events.pipe(
                filter((event) => event instanceof NavigationEnd),
                take(1),
            ),
        );
        fixture.componentInstance['closeInspector'](false);
        expect(back).toHaveBeenCalledOnce();
        await closed;
        await fixture.whenStable();
        fixture.detectChanges();
        expect(router.url).toBe('/course-management/7/exercises?filter=mine');
        expect(fixture.componentInstance['monitorVisible']()).toBe(false);
        const reopened = firstValueFrom(
            router.events.pipe(
                filter((event) => event instanceof NavigationEnd),
                take(1),
            ),
        );
        location.forward();
        await reopened;
        await fixture.whenStable();
        fixture.detectChanges();
        expect(router.url).toContain('aiRun=variant:q');
        expect(fixture.componentInstance['monitorVisible']()).toBe(true);
    });

    it('keeps known jobs visible when either refresh fails', () => {
        entries.set([running]);
        variants.loadJobs.mockReturnValue(throwError(() => new Error('offline')));
        open();
        expect(query('ai-activity-load-error')).not.toBeNull();
        expect(query('ai-activity-entry')).not.toBeNull();
    });

    it('cancels only the selected workflow after confirmation and disables repeated requests', () => {
        const pending = new Subject<void>();
        generation.cancel.mockReturnValue(pending);
        entries.set([running]);
        jobs.set([quiz]);
        open();
        const confirmations = fixture.debugElement.injector.get(TumUiConfirmationService);
        const confirm = vi.spyOn(confirmations, 'confirm');
        const component = fixture.componentInstance;
        const row = component['rows']().find((item) => item.source.kind === 'authoring')!;
        component['cancel'](row);
        expect(generation.cancel).not.toHaveBeenCalled();
        confirm.mock.calls[0][0].accept!();
        expect(generation.cancel).toHaveBeenCalledExactlyOnceWith(2, 'a');
        expect(variants.cancelJob).not.toHaveBeenCalled();
        component['cancel'](row);
        expect(confirm).toHaveBeenCalledTimes(1);
        pending.complete();
        component['cancel'](component['rows']().find((item) => item.source.kind === 'variant')!);
        confirm.mock.calls[1][0].accept!();
        expect(variants.cancelJob).toHaveBeenCalledExactlyOnceWith('q');
    });

    it('does not accept an old cancellation confirmation after persistence starts', () => {
        entries.set([running]);
        open();
        const confirm = vi.spyOn(fixture.debugElement.injector.get(TumUiConfirmationService), 'confirm');
        fixture.componentInstance['cancel'](fixture.componentInstance['rows']()[0]);
        entries.set([{ ...running, phase: 'SAVING' }]);
        confirm.mock.calls[0][0].accept!();
        expect(generation.cancel).not.toHaveBeenCalled();
    });

    it('clears presentation state and refuses stale confirmation across account changes', () => {
        entries.set([running]);
        jobs.set([quiz]);
        open();
        const confirm = vi.spyOn(fixture.debugElement.injector.get(TumUiConfirmationService), 'confirm');
        fixture.componentInstance['cancel'](fixture.componentInstance['rows']()[0]);
        fixture.componentInstance['dismiss'](fixture.componentInstance['rows']()[0]);
        identity.set(undefined);
        fixture.detectChanges();
        expect(query('ai-activity-trigger')).toBeNull();
        confirm.mock.calls[0][0].accept!();
        expect(generation.cancel).not.toHaveBeenCalled();
        identity.set({ login: 'other' });
        fixture.detectChanges();
        expect(fixture.componentInstance['hiddenCount']()).toBe(0);
    });
});
