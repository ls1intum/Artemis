import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSpinner, faTriangleExclamation, faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { PopoverModule } from 'primeng/popover';
import { ButtonModule } from 'primeng/button';
import { ProgressBarModule } from 'primeng/progressbar';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseVariantGenerationService } from 'app/hyperion/services/exercise-variant-generation.service';
import { isTerminalVariantPhase } from 'app/hyperion/services/exercise-variant-websocket.service';
import { VariantJob } from 'app/openapi/model/variantJob';
import { ExerciseVariantAiModalWizardComponent } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal-wizard.component';

/** Pipeline phases in execution order — drives the tray's slim progress bar (plan Section 5.2). */
const RUNNING_PHASE_ORDER = ['ANALYZING', 'PLANNING', 'PROVISIONING', 'TRANSFORMING', 'VERIFYING', 'REPAIRING', 'FINALIZING'] as const;

/**
 * Navbar job tray for background variant generation (plan Section 5.4).
 * Mounted in navbar.component.html. Tray = state at a glance; the generation modal = full inspection.
 */
@Component({
    selector: 'jhi-variant-generation-tray',
    templateUrl: './variant-generation-tray.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [ConfirmationService],
    imports: [FaIconComponent, PopoverModule, ButtonModule, ProgressBarModule, ConfirmDialogModule, ArtemisTranslatePipe, ExerciseVariantAiModalWizardComponent],
})
export class VariantGenerationTrayComponent {
    protected readonly variantGenerationService = inject(ExerciseVariantGenerationService);
    private readonly accountService = inject(AccountService);
    private readonly router = inject(Router);
    private readonly confirmationService = inject(ConfirmationService);
    private readonly translateService = inject(TranslateService);

    /**
     * Clicking a job entry opens the tray-hosted generation modal in monitor mode, initialized from the
     * job-detail endpoint (plan Section 5.4). Finished jobs with a variant additionally deep-link to the
     * exercise via the button rendered next to the entry.
     */
    readonly monitorJobId = signal<string | undefined>(undefined);
    readonly monitorVisible = signal(false);

    protected readonly faWandMagicSparkles = faWandMagicSparkles;
    protected readonly faSpinner = faSpinner;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly isTerminalVariantPhase = isTerminalVariantPhase;

    /** Login of the user whose jobs are currently loaded — guards against redundant re-syncs. */
    private loadedForLogin?: string;

    constructor() {
        // The navbar — and with it this tray — is instantiated BEFORE login, so a one-shot load in ngOnInit
        // ran unauthenticated, failed silently, and left the tray hidden even while jobs were running or had
        // failed in the background. Sync the job list whenever the authenticated user changes instead; the
        // per-job websocket topics keep the list live afterwards (plan Section 5.4, "State handling").
        effect(() => {
            const login = this.accountService.userIdentity()?.login;
            untracked(() => {
                if (login === this.loadedForLogin) {
                    return;
                }
                this.loadedForLogin = login;
                if (login) {
                    this.variantGenerationService.loadJobs().subscribe({ error: () => {} });
                } else {
                    this.variantGenerationService.clearJobs();
                }
            });
        });
    }

    /** Progress through the running phases as a percentage for the slim progress bar (plan Section 5.4). */
    phaseProgress(job: VariantJob): number {
        const index = RUNNING_PHASE_ORDER.indexOf(job.phase as (typeof RUNNING_PHASE_ORDER)[number]);
        if (index < 0) {
            return 100;
        }
        return Math.round(((index + 1) / RUNNING_PHASE_ORDER.length) * 100);
    }

    phaseLabelKey(job: VariantJob): string {
        return `artemisApp.exerciseVariantGeneration.phase.${job.phase}`;
    }

    /** Finished jobs with a kept variant deep-link to the type-aware editor route (plan Section 5.4). */
    openVariant(job: VariantJob): void {
        if (!job.courseId || !job.variantExerciseId) {
            return;
        }
        const typeSegment = job.exerciseType === 'quiz' ? 'quiz-exercises' : `${job.exerciseType}-exercises`;
        this.router.navigate(['/course-management', job.courseId, typeSegment, job.variantExerciseId]);
    }

    hasVariantLink(job: VariantJob): boolean {
        return (job.phase === 'COMPLETED' || job.phase === 'DRAFT_WITH_WARNINGS') && !!job.variantExerciseId && !!job.courseId;
    }

    /** Cooperative cancel behind a confirmation — discards the LLM work and deletes the clone (plan Section 5.4). */
    cancelJob(job: VariantJob, event: Event): void {
        event.stopPropagation();
        this.confirmationService.confirm({
            target: event.target as EventTarget,
            message: this.translateService.instant('artemisApp.exerciseVariantGeneration.tray.cancelConfirmation'),
            accept: () => {
                if (job.jobId) {
                    this.variantGenerationService.cancelJob(job.jobId).subscribe({ error: () => {} });
                }
            },
        });
    }

    openJobEntry(job: VariantJob): void {
        if (!job.jobId) {
            return;
        }
        this.monitorJobId.set(job.jobId);
        this.monitorVisible.set(true);
    }
}
