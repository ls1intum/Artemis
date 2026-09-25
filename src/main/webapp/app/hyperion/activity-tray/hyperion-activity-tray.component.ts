import { parseJson } from 'app/foundation/util/json.util';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { HyperionRunPageComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-page.component';
import { ChangeDetectionStrategy, Component, DestroyRef, Injector, computed, inject, input, linkedSignal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowRight, faCheck, faSpinner, faTriangleExclamation, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import {
    TumUiButtonDirective,
    TumUiConfirmDialogComponent,
    TumUiConfirmationService,
    TumUiDialogComponent,
    TumUiMessageComponent,
    TumUiPopoverComponent,
    TumUiPopoverTriggerDirective,
} from '@tumaet/ui-angular';
import { finalize, timeout } from 'rxjs';
import { getSignalBasedOnRoute } from 'app/foundation/route/getSignalBasedOnRoute';
import { IS_AT_LEAST_EDITOR } from 'app/foundation/constants/authority.constants';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HyperionJobRegistryService } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { ExerciseVariantGenerationService } from 'app/hyperion/services/exercise-variant-generation.service';
import { ExerciseVariantAiModalWizardComponent } from 'app/hyperion/variants/exercise-variant-ai-modal-wizard.component';
import { HyperionActivityRow, authoringActivity, variantActivity } from './hyperion-activity.model';

/** One header entry for AI work. Dismissal affects presentation, never the running job or recovery state. */
@Component({
    selector: 'jhi-hyperion-activity-tray',
    templateUrl: './hyperion-activity-tray.component.html',
    styleUrl: './hyperion-activity-tray.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [TumUiConfirmationService],
    imports: [
        TumUiDialogComponent,
        HyperionRunPageComponent,
        FaIconComponent,
        ArtemisTranslatePipe,
        TumUiButtonDirective,
        TumUiPopoverComponent,
        TumUiPopoverTriggerDirective,
        TumUiMessageComponent,
        TumUiConfirmDialogComponent,
        ExerciseVariantAiModalWizardComponent,
    ],
})
export class HyperionActivityTrayComponent {
    private readonly injector = inject(Injector);
    private readonly variants = inject(ExerciseVariantGenerationService);
    private readonly generation = inject(HyperionExerciseGenerationService);
    private readonly account = inject(AccountService);
    private readonly router = inject(Router);
    private readonly route = inject(ActivatedRoute);
    private readonly location = inject(Location);
    private readonly confirmations = inject(TumUiConfirmationService);
    private readonly translate = inject(TranslateService);
    private readonly alerts = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);
    readonly authoringEnabled = input(false);
    // A quiz-only deployment must not start programming-job polling against disabled endpoints.
    private readonly registry = computed(() =>
        this.authoringEnabled() && this.account.hasAnyAuthorityDirect(IS_AT_LEAST_EDITOR) ? this.injector.get(HyperionJobRegistryService) : undefined,
    );
    private readonly routedJob = getSignalBasedOnRoute(this.router, (url) => this.router.parseUrl(url).queryParamMap.get('aiRun'));
    private readonly popover = viewChild(TumUiPopoverComponent);
    private readonly login = computed(() => this.account.userIdentity()?.login);

    // Dismissal is scoped to the account and survives reloads; it never changes a job.
    private readonly dismissed = linkedSignal({
        source: this.login,
        computation: (login) => {
            try {
                const stored = parseJson<unknown>(localStorage.getItem(`ai-activity-dismissed:${login}`) ?? '[]');
                return new Set<string>(Array.isArray(stored) ? stored.filter((key): key is string => typeof key === 'string').slice(0, 500) : []);
            } catch {
                return new Set<string>();
            }
        },
    });
    private readonly acknowledged = linkedSignal({ source: this.login, computation: () => new Set<string>() });
    protected readonly showHistory = linkedSignal({ source: this.login, computation: () => false });
    protected readonly monitorJobId = computed(() => {
        const reference = this.routedJob();
        return reference?.startsWith('variant:') && reference.length > 8 ? reference.slice(8) : undefined;
    });
    protected readonly inspector = computed(() => {
        const reference = this.routedJob();
        const login = this.login();
        if (!login || !this.authoringEnabled() || !this.account.hasAnyAuthorityDirect(IS_AT_LEAST_EDITOR)) return [];
        const match = /^authoring:([1-9]\d*):([^:]+)$/.exec(reference ?? '');
        if (!match || !Number.isSafeInteger(Number(match[1]))) return [];
        return [{ key: `${login}:${reference}`, exerciseId: Number(match[1]), runId: match[2] === 'latest' ? undefined : match[2] }];
    });
    protected readonly quizInspector = computed(() => (this.monitorVisible() ? [{ key: `${this.login()}:${this.monitorJobId()}`, runId: this.monitorJobId() }] : []));
    protected readonly hasMoreHistory = computed(() => this.registry()?.hasMoreHistory() ?? false);
    protected loadMoreHistory(): void {
        this.showHistory.set(true);
        this.registry()?.loadMoreHistory();
    }
    protected readonly monitorVisible = computed(() => !!this.login() && this.account.hasAnyAuthorityDirect(IS_AT_LEAST_EDITOR) && !!this.monitorJobId());
    protected readonly cancelling = linkedSignal({ source: this.login, computation: () => new Set<string>() });
    private readonly refreshing = linkedSignal({ source: this.login, computation: () => false });
    private readonly variantsLoadFailed = linkedSignal({ source: this.login, computation: () => false });
    protected readonly loadFailed = computed(() => (this.registry()?.loadFailed() ?? false) || this.variantsLoadFailed());
    protected readonly rows = computed(() => {
        if (!this.login() || !this.account.hasAnyAuthorityDirect(IS_AT_LEAST_EDITOR)) return [];
        return [
            ...(this.authoringEnabled() ? (this.registry()?.entries() ?? []).map(authoringActivity) : []),
            ...this.variants
                .jobs()
                .filter((job) => !!job.jobId)
                .map(variantActivity),
        ].sort((a, b) => Number(b.recoveryRequired) - Number(a.recoveryRequired) || Number(b.active) - Number(a.active) || b.startedAt - a.startedAt || a.key.localeCompare(b.key));
    });
    protected readonly visibleRows = computed(() => this.rows().filter((row) => this.showHistory() || row.active || !this.dismissed().has(row.key)));
    protected readonly runningCount = computed(() => this.rows().filter((row) => row.active).length);
    protected readonly attentionCount = computed(
        () => this.visibleRows().filter((row) => row.recoveryRequired || (row.attention && !row.seen && !this.acknowledged().has(row.key))).length,
    );
    protected readonly finishedCount = computed(() => this.visibleRows().filter((row) => !row.active && !row.attention && !row.seen && !this.acknowledged().has(row.key)).length);
    protected readonly hiddenCount = computed(() => this.rows().filter((row) => !row.active && this.dismissed().has(row.key)).length);
    protected readonly icons = { faArrowRight, faCheck, faSpinner, faTriangleExclamation, faXmark, facArtemisIntelligence };

    protected open(row: HyperionActivityRow): void {
        this.popover()?.close();
        if (!row.active) this.acknowledged.update((keys) => new Set([row.key, ...keys].slice(0, 100)));
        const reference = row.source.kind === 'authoring' ? `authoring:${row.source.entry.exerciseId}:${row.source.entry.jobId}` : `variant:${row.source.job.jobId}`;
        if (row.source.kind === 'authoring') this.registry()?.markSeen(row.source.entry.jobId);
        void this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { aiRun: reference },
            queryParamsHandling: 'merge',
            preserveFragment: true,
            state: { hyperionInspector: true },
        });
    }

    /** Back/Forward follow the URL. A direct link closes in place instead of leaving the application. */
    protected closeInspector(visible: boolean): void {
        if (visible) return;
        const state = this.location.getState();
        if (typeof state === 'object' && state !== null && 'hyperionInspector' in state && state.hyperionInspector === true) {
            this.location.back();
        } else {
            void this.router.navigate([], { relativeTo: this.route, queryParams: { aiRun: null }, queryParamsHandling: 'merge', preserveFragment: true, replaceUrl: true });
        }
    }

    protected dismiss(row: HyperionActivityRow): void {
        if (row.active) return;
        this.showHistory.set(false);
        this.dismissed.update((keys) => new Set([row.key, ...keys].slice(0, 500)));
        try {
            localStorage.setItem(`ai-activity-dismissed:${this.login()}`, JSON.stringify([...this.dismissed()]));
        } catch {
            // Storage can be unavailable; dismissal still works for this session.
        }
        if (!this.visibleRows().length) this.popover()?.close();
    }

    protected refresh(open: boolean): void {
        if (!open || this.refreshing()) return;
        this.registry()?.refresh();
        this.refreshing.set(true);
        const login = this.login();
        this.variants
            .loadJobs()
            .pipe(
                timeout(10_000),
                takeUntilDestroyed(this.destroyRef),
                finalize(() => {
                    if (this.login() === login) this.refreshing.set(false);
                }),
            )
            .subscribe({
                next: () => {
                    if (this.login() === login) this.variantsLoadFailed.set(false);
                },
                error: () => {
                    if (this.login() === login) this.variantsLoadFailed.set(true);
                },
            });
    }

    protected cancel(row: HyperionActivityRow): void {
        if (!row.canCancel || this.cancelling().has(row.key)) return;
        const login = this.login();
        this.confirmations.confirm({
            header: this.translate.instant('artemisApp.exerciseVariantGeneration.tray.cancelConfirmationHeader'),
            message: this.translate.instant(
                row.source.kind === 'variant' ? 'artemisApp.exerciseVariantGeneration.tray.cancelConfirmation' : 'artemisApp.hyperion.activity.cancelAuthoring',
            ),
            acceptLabel: this.translate.instant('artemisApp.exerciseVariantGeneration.tray.cancelConfirmationAccept'),
            rejectLabel: this.translate.instant('artemisApp.exerciseVariantGeneration.tray.cancelConfirmationReject'),
            acceptSeverity: 'danger',
            icon: faTriangleExclamation,
            accept: () => {
                if (this.login() !== login || !this.rows().some((current) => current.key === row.key && current.canCancel) || this.cancelling().has(row.key)) return;
                this.cancelling.update((keys) => new Set([...keys, row.key]));
                const request =
                    row.source.kind === 'authoring' ? this.generation.cancel(row.source.entry.exerciseId, row.source.entry.jobId) : this.variants.cancelJob(row.source.job.jobId!);
                request
                    .pipe(
                        takeUntilDestroyed(this.destroyRef),
                        finalize(() => {
                            if (this.login() === login) this.cancelling.update((keys) => new Set([...keys].filter((key) => key !== row.key)));
                        }),
                    )
                    .subscribe({
                        error: () => {
                            if (this.login() === login) this.alerts.error('artemisApp.exerciseVariantGeneration.tray.cancelFailed');
                        },
                    });
            },
        });
    }
}
