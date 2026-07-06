import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Router } from '@angular/router';
import { computed, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from 'primeng/api';
import { of } from 'rxjs';
import { MockComponent, MockPipe } from 'ng-mocks';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { VariantGenerationTrayComponent } from 'app/core/navbar/variant-generation-tray/variant-generation-tray.component';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { ExerciseVariantGenerationService } from 'app/hyperion/services/exercise-variant-generation.service';
import { isTerminalVariantPhase } from 'app/hyperion/services/exercise-variant-websocket.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseVariantAiModalWizardComponent } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal-wizard.component';
import { VariantJob } from 'app/openapi/model/variantJob';

/**
 * Vitest specs for the navbar job tray (plan Sections 5.4 "State handling" and 10 "Client tests").
 */
describe('VariantGenerationTrayComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<VariantGenerationTrayComponent>;
    let component: VariantGenerationTrayComponent;
    let jobs: ReturnType<typeof signal<VariantJob[]>>;
    let serviceMock: {
        jobs: typeof jobs;
        runningJobs: unknown;
        hasJobs: unknown;
        loadJobs: ReturnType<typeof vi.fn>;
        clearJobs: ReturnType<typeof vi.fn>;
        cancelJob: ReturnType<typeof vi.fn>;
    };
    let routerMock: { navigate: ReturnType<typeof vi.fn> };
    let userIdentity: ReturnType<typeof signal<User | undefined>>;

    const runningJob: VariantJob = { jobId: 'job-1', sourceExerciseId: 42, courseId: 7, sourceExerciseTitle: 'Sorting Basics', exerciseType: 'programming', phase: 'TRANSFORMING' };
    const completedJob: VariantJob = {
        jobId: 'job-2',
        sourceExerciseId: 43,
        courseId: 7,
        sourceExerciseTitle: 'Quiz 1',
        exerciseType: 'quiz',
        phase: 'COMPLETED',
        variantExerciseId: 4711,
    };

    beforeEach(async () => {
        jobs = signal<VariantJob[]>([]);
        serviceMock = {
            jobs,
            runningJobs: computed(() => jobs().filter((job) => !isTerminalVariantPhase(job.phase))),
            hasJobs: computed(() => jobs().length > 0),
            loadJobs: vi.fn().mockReturnValue(of([])),
            clearJobs: vi.fn(),
            cancelJob: vi.fn().mockReturnValue(of(undefined)),
        };
        routerMock = { navigate: vi.fn() };
        userIdentity = signal<User | undefined>({ login: 'instructor1' } as User);

        await TestBed.configureTestingModule({
            imports: [VariantGenerationTrayComponent],
            providers: [
                { provide: ExerciseVariantGenerationService, useValue: serviceMock },
                { provide: AccountService, useValue: { userIdentity } },
                { provide: Router, useValue: routerMock },
                {
                    provide: TranslateService,
                    useValue: { instant: (key: string) => key, get: (key: string) => of(key), onLangChange: of(), onTranslationChange: of(), onDefaultLangChange: of() },
                },
            ],
        })
            .overrideComponent(VariantGenerationTrayComponent, {
                remove: { imports: [ArtemisTranslatePipe, ExerciseVariantAiModalWizardComponent] },
                add: { imports: [MockPipe(ArtemisTranslatePipe, (key) => key), MockComponent(ExerciseVariantAiModalWizardComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(VariantGenerationTrayComponent);
        component = fixture.componentInstance;
    });

    it('syncs the job list when the user logs in and clears it on logout', () => {
        userIdentity.set(undefined);
        fixture.detectChanges();
        expect(serviceMock.loadJobs).not.toHaveBeenCalled();

        userIdentity.set({ login: 'instructor1' } as User);
        fixture.detectChanges();
        expect(serviceMock.loadJobs).toHaveBeenCalledTimes(1);

        // A refreshed identity object for the SAME user must not trigger a redundant re-sync.
        userIdentity.set({ login: 'instructor1' } as User);
        fixture.detectChanges();
        expect(serviceMock.loadJobs).toHaveBeenCalledTimes(1);

        userIdentity.set(undefined);
        fixture.detectChanges();
        expect(serviceMock.clearJobs).toHaveBeenCalled();
    });

    it('is hidden without jobs and shows the spinner + count badge for a running job', () => {
        fixture.detectChanges();
        expect(serviceMock.loadJobs).toHaveBeenCalled();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-generation-tray"]')).toBeNull();

        jobs.set([runningJob]);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-generation-tray"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-tray-spinner"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-tray-count"]')?.textContent?.trim()).toBe('1');
    });

    it('hides the spinner and badge when all jobs are terminal', () => {
        jobs.set([completedJob]);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-generation-tray"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-tray-spinner"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-tray-count"]')).toBeNull();
    });

    it('computes progress from the phase position within the running order', () => {
        expect(component.phaseProgress({ phase: 'ANALYZING' } as VariantJob)).toBeGreaterThan(0);
        expect(component.phaseProgress({ phase: 'TRANSFORMING' } as VariantJob)).toBeLessThan(component.phaseProgress({ phase: 'FINALIZING' } as VariantJob));
        expect(component.phaseProgress({ phase: 'COMPLETED' } as VariantJob)).toBe(100);
    });

    it('deep-links a finished job to the type-aware editor route', () => {
        component.openVariant(completedJob);
        expect(routerMock.navigate).toHaveBeenCalledWith(['/course-management', 7, 'quiz-exercises', 4711]);

        component.openVariant(Object.assign({}, completedJob, { exerciseType: 'programming' as const }));
        expect(routerMock.navigate).toHaveBeenCalledWith(['/course-management', 7, 'programming-exercises', 4711]);
    });

    it('offers the variant link only for terminal phases that kept the variant', () => {
        expect(component.hasVariantLink(completedJob)).toBe(true);
        expect(component.hasVariantLink(Object.assign({}, completedJob, { phase: 'DRAFT_WITH_WARNINGS' as const }))).toBe(true);
        expect(component.hasVariantLink(Object.assign({}, completedJob, { phase: 'CANCELLED' as const, variantExerciseId: undefined }))).toBe(false);
        expect(component.hasVariantLink(Object.assign({}, completedJob, { phase: 'FAILED' as const, variantExerciseId: undefined }))).toBe(false);
        expect(component.hasVariantLink(runningJob)).toBe(false);
    });

    it('opens a clicked entry in monitor mode via the tray-hosted wizard', () => {
        component.openJobEntry(runningJob);
        expect(component.monitorJobId()).toBe('job-1');
        expect(component.monitorVisible()).toBe(true);
    });

    it('cancels a job only after the confirmation is accepted', () => {
        jobs.set([runningJob]);
        fixture.detectChanges();
        const confirmationService = fixture.debugElement.injector.get(ConfirmationService);
        const confirmSpy = vi.spyOn(confirmationService, 'confirm').mockImplementation((options) => {
            options.accept?.();
            return confirmationService;
        });

        component.cancelJob(runningJob, new Event('click'));

        expect(confirmSpy).toHaveBeenCalled();
        expect(serviceMock.cancelJob).toHaveBeenCalledWith('job-1');
    });
});
