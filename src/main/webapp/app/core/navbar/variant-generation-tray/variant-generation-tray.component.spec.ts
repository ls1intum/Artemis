import { TumUiConfirmationService } from '@tumaet/ui-angular';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { computed, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

import { of } from 'rxjs';
import { MockComponent, MockPipe } from 'ng-mocks';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { VariantGenerationTrayComponent } from 'app/core/navbar/variant-generation-tray/variant-generation-tray.component';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { ExerciseVariantGenerationService } from 'app/hyperion/services/exercise-variant-generation.service';
import { isTerminalVariantPhase } from 'app/hyperion/services/exercise-variant-websocket.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseVariantAiModalWizardComponent } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal-wizard.component';
import { VariantJob } from 'app/openapi/model/variant-job';

/**
 * Vitest specs for the navbar job tray.
 */
describe('VariantGenerationTrayComponent', () => {
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
    /** Whether the mocked account passes the tray's IS_AT_LEAST_EDITOR check. */
    let isEditor: boolean;

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
        isEditor = true;

        await TestBed.configureTestingModule({
            imports: [VariantGenerationTrayComponent],
            providers: [
                { provide: ExerciseVariantGenerationService, useValue: serviceMock },
                { provide: AccountService, useValue: { userIdentity, hasAnyAuthorityDirect: () => isEditor } },
                { provide: Router, useValue: routerMock },
                {
                    provide: TranslateService,
                    useValue: { instant: (key: string) => key, get: (key: string) => of(key), onLangChange: of(), onTranslationChange: of(), onDefaultLangChange: of() },
                },
            ],
        })
            .overrideComponent(VariantGenerationTrayComponent, {
                remove: { imports: [ArtemisTranslatePipe, ExerciseVariantAiModalWizardComponent] },
                add: { imports: [MockPipe(ArtemisTranslatePipe, (key) => key ?? ''), MockComponent(ExerciseVariantAiModalWizardComponent)] },
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

    it('does not query the editor-only job endpoint for a student', () => {
        // The endpoint is @EnforceAtLeastEditor; fetching as a student only produced a 403.
        isEditor = false;
        userIdentity.set({ login: 'student1' } as User);
        fixture.detectChanges();

        expect(serviceMock.loadJobs).not.toHaveBeenCalled();
        expect(serviceMock.clearJobs).toHaveBeenCalled();
    });

    it('is hidden without jobs and shows the icon-only spinner status for a running job', () => {
        fixture.detectChanges();
        expect(serviceMock.loadJobs).toHaveBeenCalled();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-generation-tray"]')).toBeNull();

        jobs.set([runningJob]);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-generation-tray"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-tray-spinner"]')).not.toBeNull();
        // Icon-only button — no count badge.
        expect(fixture.nativeElement.querySelector('[data-testid="variant-tray-count"]')).toBeNull();
    });

    it('shows the checkmark when all jobs finished successfully and the warning when one needs attention', () => {
        jobs.set([completedJob]);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-tray-spinner"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-tray-success"]')).not.toBeNull();

        jobs.set([completedJob, cloneWith(completedJob, { jobId: 'job-3', phase: 'FAILED' as const })]);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-tray-success"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-tray-attention"]')).not.toBeNull();

        // A running job wins over finished states.
        jobs.set([completedJob, runningJob]);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-tray-spinner"]')).not.toBeNull();
    });

    it('always opens a clicked entry in the tray-hosted modal — running and finished alike', () => {
        component.openJobEntry(runningJob);
        expect(component.monitorJobId()).toBe('job-1');
        expect(component.monitorVisible()).toBe(true);

        component.openJobEntry(completedJob);
        expect(component.monitorJobId()).toBe('job-2');
        expect(component.monitorVisible()).toBe(true);
        expect(routerMock.navigate).not.toHaveBeenCalled();
    });

    it('opens an entry on Space without scrolling the page behind the tray', () => {
        // The entry is a div with role="button" (it hosts the cancel button, so it cannot be a real button).
        // That contract requires Space to activate it, and Space must not also scroll the page.
        const event = { preventDefault: vi.fn() } as unknown as Event;

        component.openJobEntryOnSpace(event, runningJob);

        expect(event.preventDefault).toHaveBeenCalled();
        expect(component.monitorJobId()).toBe('job-1');
        expect(component.monitorVisible()).toBe(true);
    });

    it('flags failed jobs and drafts with warnings as needing attention', () => {
        expect(component.needsAttention(runningJob)).toBe(false);
        expect(component.needsAttention(completedJob)).toBe(false);
        expect(component.needsAttention(cloneWith(completedJob, { phase: 'FAILED' as const }))).toBe(true);
        expect(component.needsAttention(cloneWith(completedJob, { phase: 'DRAFT_WITH_WARNINGS' as const }))).toBe(true);
    });

    it('flags a cancelled job whose clone survived the cleanup and shows the cleanup note', () => {
        // A cancellation deletes the clone, so an ordinary CANCELLED entry carries no exercise id.
        const ordinaryCancellation = cloneWith(completedJob, { phase: 'CANCELLED' as const, variantExerciseId: undefined });
        expect(component.hasLeftoverExercise(ordinaryCancellation)).toBe(false);
        expect(component.needsAttention(ordinaryCancellation)).toBe(false);

        const leftover = cloneWith(completedJob, { phase: 'CANCELLED' as const, failureDetail: 'The generated exercise (id 4711) could not be deleted automatically.' });
        expect(component.hasLeftoverExercise(leftover)).toBe(true);
        expect(component.needsAttention(leftover)).toBe(true);

        // The tray button itself must carry the warning status, not the neutral checkmark.
        jobs.set([leftover]);
        fixture.detectChanges();
        expect(component.trayStatus()).toBe('attention');
        expect(fixture.nativeElement.querySelector('[data-testid="variant-tray-attention"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-tray-success"]')).toBeNull();
    });

    it('maps phases onto the step-dot timeline and off it for terminal phases', () => {
        expect(component.phaseIndex({ phase: 'ANALYZING' } as VariantJob)).toBe(0);
        expect(component.phaseIndex({ phase: 'TRANSFORMING' } as VariantJob)).toBeGreaterThan(component.phaseIndex({ phase: 'PLANNING' } as VariantJob));
        expect(component.phaseIndex({ phase: 'COMPLETED' } as VariantJob)).toBe(-1);
    });

    it('cancels a job only after the confirmation is accepted', () => {
        jobs.set([runningJob]);
        fixture.detectChanges();
        const confirmationService = fixture.debugElement.injector.get(TumUiConfirmationService);
        const confirmSpy = vi.spyOn(confirmationService, 'confirm').mockImplementation((options) => options.accept());

        component.cancelJob(runningJob, new Event('click'));

        expect(confirmSpy).toHaveBeenCalled();
        expect(serviceMock.cancelJob).toHaveBeenCalledWith('job-1');
    });
});
