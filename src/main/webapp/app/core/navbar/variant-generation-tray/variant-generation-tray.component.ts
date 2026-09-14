import { TumUiButtonComponent, TumUiConfirmDialogComponent, TumUiConfirmationService, TumUiPopoverComponent, TumUiPopoverTriggerDirective } from '@tumaet/ui-angular';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { IS_AT_LEAST_EDITOR } from 'app/foundation/constants/authority.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowRight, faCheck, faCircleCheck, faExclamation, faSpinner, faTriangleExclamation, faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';

import { TranslateService } from '@ngx-translate/core';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseVariantGenerationService } from 'app/hyperion/services/exercise-variant-generation.service';
import { isTerminalVariantPhase } from 'app/hyperion/services/exercise-variant-websocket.service';
import { VariantJob } from 'app/openapi/model/variant-job';
import { VariantGenerationRequest } from 'app/openapi/model/variant-generation-request';
import { ExerciseVariantAiModalWizardComponent } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal-wizard.component';
import { adaptationChips } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal.utils';

/** Pipeline phases in execution order — drives the tray's per-entry step-dot timeline. */
const RUNNING_PHASE_ORDER = ['ANALYZING', 'PLANNING', 'PROVISIONING', 'TRANSFORMING', 'VERIFYING', 'REPAIRING', 'FINALIZING'] as const;

/** Aggregated tray-button state: spinner while anything runs, then checkmark or warning. */
type TrayStatus = 'running' | 'success' | 'attention';

/**
 * Navbar job tray for background variant generation, mounted in the navbar's right-side icon menu.
 * Tray = state at a glance; the generation modal = full inspection.
 */
@Component({
    selector: 'jhi-variant-generation-tray',
    templateUrl: './variant-generation-tray.component.html',
    styleUrl: './variant-generation-tray.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [TumUiConfirmationService],
    imports: [
        FaIconComponent,
        TumUiPopoverComponent,
        TumUiPopoverTriggerDirective,
        TumUiButtonComponent,
        TumUiConfirmDialogComponent,
        ArtemisTranslatePipe,
        ExerciseVariantAiModalWizardComponent,
    ],
})
export class VariantGenerationTrayComponent {
    protected readonly variantGenerationService = inject(ExerciseVariantGenerationService);
    private readonly accountService = inject(AccountService);
    private readonly confirmationService = inject(TumUiConfirmationService);
    private readonly translateService = inject(TranslateService);
    private readonly alertService = inject(AlertService);

    /**
     * Clicking a job entry opens the tray-hosted generation modal in monitor mode, initialized from the
     * job-detail endpoint; the modal's "Open in Editor" button is the only path that navigates to the variant.
     */
    readonly monitorJobId = signal<string | undefined>(undefined);
    readonly monitorVisible = signal(false);

    private readonly trayPopover = viewChild<TumUiPopoverComponent>('trayPopover');

    /**
     * Icon-only status of the tray button: spinner while any job runs, warning once all finished but at least
     * one needs attention (failed or draft with warnings), checkmark otherwise.
     */
    readonly trayStatus = computed<TrayStatus>(() => {
        if (this.variantGenerationService.runningJobs().length > 0) {
            return 'running';
        }
        const needsAttention = this.variantGenerationService.jobs().some((job) => this.needsAttention(job));
        return needsAttention ? 'attention' : 'success';
    });

    protected readonly faWandMagicSparkles = faWandMagicSparkles;
    protected readonly faArrowRight = faArrowRight;
    protected readonly faSpinner = faSpinner;
    protected readonly faCheck = faCheck;
    protected readonly faExclamation = faExclamation;
    protected readonly faCircleCheck = faCircleCheck;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly isTerminalVariantPhase = isTerminalVariantPhase;
    protected readonly runningPhases = RUNNING_PHASE_ORDER;

    /** Login of the user whose jobs are currently loaded — guards against redundant re-syncs. */
    private loadedForLogin?: string;

    constructor() {
        // The navbar — and with it this tray — is instantiated BEFORE login, so a one-shot load in ngOnInit
        // ran unauthenticated, failed silently, and left the tray hidden even while jobs were running or had
        // failed in the background. Sync the job list whenever the authenticated user changes instead; the
        // per-job websocket topics keep the list live afterwards.
        effect(() => {
            const login = this.accountService.userIdentity()?.login;
            untracked(() => {
                if (login === this.loadedForLogin) {
                    return;
                }
                this.loadedForLogin = login;
                // Variant generation is an editor tool: the job endpoint is @EnforceAtLeastEditor, so fetching as
                // a student produced nothing but a 403 in the console. Mirror the server's rule here — a user who
                // cannot generate variants has no jobs to show, and the tray stays hidden either way.
                if (login && this.accountService.hasAnyAuthorityDirect(IS_AT_LEAST_EDITOR)) {
                    this.variantGenerationService.loadJobs().subscribe({ error: () => {} });
                } else {
                    this.variantGenerationService.clearJobs();
                }
            });
        });
    }

    /** "What is being adapted" chips per card — same helper the generation modal uses. */
    adaptationChips(request: VariantGenerationRequest | undefined): string[] {
        return adaptationChips(request, (key, params) => this.translateService.instant(key, params));
    }

    /** REST re-sync on tray open — websocket events don't carry title/request updates. */
    refreshJobs(): void {
        this.variantGenerationService.loadJobs().subscribe({ error: () => {} });
    }

    /** Index of the job's phase in the running order — done/active/pending classes of the step dots. */
    phaseIndex(job: VariantJob): number {
        return RUNNING_PHASE_ORDER.indexOf(job.phase as (typeof RUNNING_PHASE_ORDER)[number]);
    }

    phaseLabelKey(job: VariantJob): string {
        return `artemisApp.exerciseVariantGeneration.phase.${job.phase}`;
    }

    /** Entries needing instructor attention get the warning accent in the list. */
    needsAttention(job: VariantJob): boolean {
        return job.phase === 'FAILED' || job.phase === 'DRAFT_WITH_WARNINGS' || this.hasLeftoverExercise(job);
    }

    /**
     * A cancellation deletes the generated clone, so a CANCELLED entry normally has no exercise id. When it
     * still carries one, that deletion failed and the exercise survives — the instructor has to delete it by
     * hand, which makes this entry anything but an ordinary cancellation.
     */
    hasLeftoverExercise(job: VariantJob): boolean {
        return job.phase === 'CANCELLED' && job.variantExerciseId !== undefined;
    }

    /** Cooperative cancel behind a confirmation — discards the LLM work and deletes the clone. */
    cancelJob(job: VariantJob, event: Event): void {
        event.stopPropagation();
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.exerciseVariantGeneration.tray.cancelConfirmationHeader'),
            message: this.translateService.instant('artemisApp.exerciseVariantGeneration.tray.cancelConfirmation'),
            acceptLabel: this.translateService.instant('artemisApp.exerciseVariantGeneration.tray.cancelConfirmationAccept'),
            rejectLabel: this.translateService.instant('artemisApp.exerciseVariantGeneration.tray.cancelConfirmationReject'),
            acceptSeverity: 'danger',
            icon: faTriangleExclamation,
            accept: () => {
                if (job.jobId) {
                    this.variantGenerationService
                        .cancelJob(job.jobId)
                        .subscribe({ error: () => this.alertService.error('artemisApp.exerciseVariantGeneration.tray.cancelFailed') });
                }
            },
        });
    }

    /**
     * Card click always opens the generation modal (running: live timeline; finished: summary) — navigation
     * to the exercise happens via the modal's "Open in Editor" button, which the wizard handles itself.
     */
    openJobEntry(job: VariantJob): void {
        if (!job.jobId) {
            return;
        }
        this.trayPopover()?.close();
        this.monitorJobId.set(job.jobId);
        this.monitorVisible.set(true);
    }

    /**
     * Space activation for the entry. The entry carries `role="button"`, so it must respond to Space as well as
     * Enter; the default has to be suppressed or the key scrolls the page behind the popover instead.
     *
     * @param event the keyboard event to suppress
     * @param job   the job whose entry was activated
     */
    openJobEntryOnSpace(event: Event, job: VariantJob): void {
        event.preventDefault();
        this.openJobEntry(job);
    }
}
