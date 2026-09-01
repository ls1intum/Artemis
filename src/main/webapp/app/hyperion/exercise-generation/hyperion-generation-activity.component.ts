import {
    TumUiButtonComponent,
    TumUiButtonDirective,
    TumUiDialogComponent,
    TumUiMessageComponent,
    TumUiStatusDotComponent,
    TumUiStatusDotState,
    TumUiTagComponent,
    TumUiTooltipDirective,
} from '@tumaet/ui-angular';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output } from '@angular/core';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faRotateLeft, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HyperionGenerationActivityFacade, HyperionGenerationCompletedEvent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.facade';
import { displayFileChangePath, latestTerminalEvent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.utils';
import { activityView } from 'app/hyperion/exercise-generation/model/hyperion-generation-activity';
import { runOutcome, stageStates } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';
import { HyperionRunProgressComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-progress.component';
import { ExerciseGenerationFileChange, HyperionFileChangeRepo, HyperionGenerationMode } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

const REPO_ORDER: HyperionFileChangeRepo[] = ['solution', 'template', 'tests', 'other'];

/** One changed file with everything the template needs already resolved, so no binding calls a method. */
interface RepoFileEntry {
    key: string;
    file: ExerciseGenerationFileChange;
    displayPath: string;
    accessibleLabel: string;
    navigable: boolean;
}

interface RepoFileGroup {
    repo: HyperionFileChangeRepo;
    files: RepoFileEntry[];
}

interface ActivityLiveStatus {
    message?: string;
    labelKey: string;
    busy: boolean;
}

export type HyperionReviewTarget = 'problem-statement' | 'solution' | 'template' | 'tests';

export interface HyperionReviewRequestedEvent {
    target: HyperionReviewTarget;
    jobId: string;
    commitHash?: string;
    savedExerciseVersionId?: number;
}

export type { HyperionGenerationCompletedEvent } from './hyperion-generation-activity.facade';

/**
 * The code editor's AI activity panel: a compact monitor for the exercise's current Hyperion run.
 *
 * It is deliberately *not* a second run page. It reports the run's state, renders the one shared progress ladder,
 * offers the actions that only make sense next to the editor (cancel, run again, undo, reload, jump to a saved
 * change), and links to the full run page for everything else.
 */
@Component({
    selector: 'jhi-hyperion-generation-activity',
    templateUrl: './hyperion-generation-activity.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [HyperionGenerationActivityFacade],
    imports: [
        FaIconComponent,
        RouterLink,
        TranslateDirective,
        ArtemisTranslatePipe,
        HyperionRunProgressComponent,
        TumUiButtonComponent,
        TumUiButtonDirective,
        TumUiDialogComponent,
        TumUiMessageComponent,
        TumUiStatusDotComponent,
        TumUiTagComponent,
        TumUiTooltipDirective,
    ],
})
export class HyperionGenerationActivityComponent {
    private readonly facade = inject(HyperionGenerationActivityFacade);
    private readonly translateService = inject(TranslateService);
    private readonly liveAnnouncer = inject(LiveAnnouncer);

    readonly exerciseId = input<number | undefined>();
    /** Only needed to build the link to this exercise's run page; the panel works without it. */
    readonly courseId = input<number | undefined>();
    readonly startAllowed = input(true);
    readonly refreshingEditor = input(false);
    readonly editorRefreshFailed = input(false);
    readonly editorRefreshRequested = output<void>();
    readonly generationReverted = output<string>();
    readonly generationCompleted = output<HyperionGenerationCompletedEvent>();
    readonly fileChangeSelected = output<ExerciseGenerationFileChange>();
    readonly reviewRequested = output<HyperionReviewRequestedEvent>();
    readonly startRequested = output<HyperionGenerationMode | undefined>();

    readonly jobId = this.facade.jobId;
    readonly mode = this.facade.mode;
    readonly running = this.facade.running;
    readonly statusLoading = this.facade.statusLoading;
    readonly statusLoadFailed = this.facade.statusLoadFailed;
    readonly events = this.facade.events;
    readonly fileChanges = this.facade.fileChanges;
    readonly verdict = this.facade.verdict;
    readonly completionStatus = this.facade.completionStatus;
    readonly liveExerciseChanged = this.facade.liveExerciseChanged;
    readonly revertAvailable = this.facade.revertAvailable;
    readonly revertJobId = this.facade.revertJobId;
    readonly revertMode = this.facade.revertMode;
    readonly ownedByCaller = this.facade.ownedByCaller;
    readonly cancellable = this.facade.cancellable;
    readonly revertedMode = this.facade.revertedMode;
    readonly cancelRequested = this.facade.cancelRequested;
    readonly confirmRevertVisible = this.facade.confirmRevertVisible;
    readonly reverting = this.facade.reverting;
    readonly reverted = this.facade.reverted;
    readonly revertPartialRepositories = this.facade.revertPartialRepositories;

    /** The run's outcome, scanned once and shared by everything that reports on it. */
    private readonly terminalEvent = computed(() => latestTerminalEvent(this.events()));

    readonly visible = computed(() => this.exerciseId() !== undefined);
    readonly idle = computed(() => this.jobId() === undefined && !this.statusLoading() && !this.statusLoadFailed() && !this.editorRefreshFailed() && !this.reverted());
    readonly titleLabelKey = computed(() => (this.mode() === 'ADAPT' ? 'artemisApp.hyperion.generationActivity.adaptationTitle' : 'artemisApp.hyperion.generationActivity.title'));
    readonly runningLabelKey = computed(() => {
        if (this.running() && !this.cancellable()) {
            return 'artemisApp.hyperion.generationActivity.finalizing';
        }
        return this.mode() === 'ADAPT' ? 'artemisApp.hyperion.generationActivity.adapting' : 'artemisApp.hyperion.generationActivity.running';
    });
    readonly canRevert = this.facade.canRevert;
    readonly canRunAgain = computed(() => {
        if (!this.startAllowed() || this.running() || this.statusLoading()) {
            return false;
        }
        const terminal = this.terminalEvent();
        return terminal?.type === 'ERROR' || terminal?.type === 'CANCELLED' || (terminal?.type === 'DONE' && terminal.completionStatus === 'PARTIAL');
    });
    readonly effectiveRevertMode = this.facade.effectiveRevertMode;
    readonly undoLabelKey = computed(() =>
        this.effectiveRevertMode() === 'GENERATE' ? 'artemisApp.hyperion.generationActivity.undoGeneration' : 'artemisApp.hyperion.generationActivity.undoAdaptation',
    );
    readonly undoTooltipKey = computed(() =>
        this.effectiveRevertMode() === 'GENERATE' ? 'artemisApp.hyperion.generationActivity.undoGenerationTooltip' : 'artemisApp.hyperion.generationActivity.undoAdaptationTooltip',
    );
    readonly undoneLabelKey = computed(() =>
        this.effectiveRevertMode() === 'GENERATE' ? 'artemisApp.hyperion.generationActivity.generationUndone' : 'artemisApp.hyperion.generationActivity.adaptationUndone',
    );
    readonly undoConfirmHeaderKey = computed(() =>
        this.effectiveRevertMode() === 'GENERATE'
            ? 'artemisApp.hyperion.generationActivity.undoGenerationConfirmHeader'
            : 'artemisApp.hyperion.generationActivity.undoAdaptationConfirmHeader',
    );
    readonly undoConfirmMessageKey = computed(() =>
        this.effectiveRevertMode() === 'GENERATE'
            ? 'artemisApp.hyperion.generationActivity.undoGenerationConfirmMessage'
            : 'artemisApp.hyperion.generationActivity.undoAdaptationConfirmMessage',
    );
    /** How the run ended, or `undefined` while it is still going. Shared with the run page through the same helper. */
    private readonly outcome = computed(() => runOutcome(this.events()));
    /** The one progress ladder, rendered by `jhi-hyperion-run-progress`. */
    readonly stages = computed(() => stageStates(this.events(), this.outcome()));
    /** The agent's activity, reported inside that ladder rather than in a detail region of its own. */
    readonly activityView = computed(() => activityView(this.events(), this.outcome()));
    readonly liveMessage = computed(() => this.events().findLast((event) => event.message)?.message);
    readonly repairRound = computed(() => this.events().findLast((event) => event.repairRound)?.repairRound);
    /**
     * The run's state as one dot plus one word. The dot never carries the meaning on its own: the word next to it is
     * the accessible name of the indicator, so the state survives both greyscale and a screen reader.
     */
    readonly runStatus = computed<{ state: TumUiStatusDotState; labelKey: string }>(() => {
        if (this.cancelRequested()) {
            return { state: 'running', labelKey: 'artemisApp.hyperion.generation.status.cancelling' };
        }
        switch (this.outcome()) {
            case 'saved':
                return { state: 'success', labelKey: 'artemisApp.hyperion.generation.status.saved' };
            case 'needsReview':
                return { state: 'warning', labelKey: 'artemisApp.hyperion.generation.status.needsReview' };
            case 'partial':
                return { state: 'warning', labelKey: 'artemisApp.hyperion.generation.status.partial' };
            case 'failed':
                return { state: 'error', labelKey: 'artemisApp.hyperion.generation.status.failed' };
            case 'cancelled':
                return { state: 'neutral', labelKey: 'artemisApp.hyperion.generation.status.cancelled' };
        }
        if (this.running()) {
            return { state: 'running', labelKey: 'artemisApp.hyperion.generation.status.running' };
        }
        if (this.statusLoading()) {
            return { state: 'queued', labelKey: 'artemisApp.hyperion.generation.status.queued' };
        }
        return { state: 'neutral', labelKey: 'artemisApp.hyperion.generation.status.unknown' };
    });
    /** The full run page for this exercise, once both identifiers are known. */
    readonly runPageLink = computed(() => {
        const courseId = this.courseId();
        const exerciseId = this.exerciseId();
        return courseId !== undefined && exerciseId !== undefined ? ['/course-management', courseId, 'programming-exercises', exerciseId, 'generation'] : undefined;
    });
    readonly filesByRepo = computed<RepoFileGroup[]>(() => {
        const entries = this.fileChanges().map<RepoFileEntry>((file) => ({
            key: `${file.repo}:${file.path}`,
            file,
            displayPath: displayFileChangePath(file),
            accessibleLabel: `${this.translateService.instant(`artemisApp.hyperion.generationActivity.repo.${file.repo}`)}: ${displayFileChangePath(file)}`,
            navigable: this.canNavigateFileChange(file),
        }));
        return REPO_ORDER.map((repo) => ({
            repo,
            files: entries.filter((entry) => entry.file.repo === repo).sort((first, second) => first.displayPath.localeCompare(second.displayPath)),
        })).filter((group) => group.files.length > 0);
    });
    readonly liveStatus = computed<ActivityLiveStatus | undefined>(() => {
        if (this.refreshingEditor()) {
            return { labelKey: 'artemisApp.hyperion.generationActivity.refreshingEditor', busy: true };
        }
        if (this.editorRefreshFailed()) {
            return { labelKey: 'artemisApp.hyperion.generationActivity.editorRefreshFailed', busy: false };
        }
        const terminal = this.terminalEvent();
        if (terminal) {
            return { labelKey: `artemisApp.hyperion.generationActivity.terminalStatus.${terminal.type}`, busy: false };
        }
        if (this.statusLoading() && !this.running()) {
            return { labelKey: 'artemisApp.hyperion.generationActivity.checkingStatus', busy: true };
        }
        if (this.running()) {
            return { labelKey: this.ownedByCaller() ? this.runningLabelKey() : 'artemisApp.hyperion.generationActivity.runningByAnotherInstructor', busy: true };
        }
        return undefined;
    });
    readonly terminalStatus = computed(() => {
        const type = this.terminalEvent()?.type;
        if (type === 'CANCELLED') {
            return { labelKey: 'artemisApp.hyperion.generationActivity.terminalStatus.CANCELLED', severity: 'secondary' as const };
        }
        if (type === 'ERROR') {
            return { labelKey: 'artemisApp.hyperion.generationActivity.terminalStatus.ERROR', severity: 'danger' as const };
        }
        return undefined;
    });
    readonly terminalMessage = computed(() => this.terminalEvent()?.message);
    readonly persistenceState = computed(() => {
        if (this.running()) {
            // A run that has not written anything yet is the healthy state, so it is stated, not warned about.
            return { labelKey: 'artemisApp.hyperion.generationActivity.persistence.workingCopy', severity: 'info' as const };
        }
        const terminal = this.terminalEvent();
        if (terminal?.type === 'DONE') {
            if (terminal.completionStatus === 'PARTIAL') {
                return { labelKey: 'artemisApp.hyperion.generationActivity.persistence.partial', severity: 'danger' as const };
            }
            if (terminal.liveExerciseChanged) {
                return terminal.completionStatus === 'NEEDS_REVIEW'
                    ? { labelKey: 'artemisApp.hyperion.generationActivity.persistence.savedNeedsReview', severity: 'warn' as const }
                    : { labelKey: 'artemisApp.hyperion.generationActivity.persistence.saved', severity: 'success' as const };
            }
            return { labelKey: 'artemisApp.hyperion.generationActivity.persistence.notSaved', severity: 'danger' as const };
        }
        if (terminal?.type === 'CANCELLED') {
            return { labelKey: 'artemisApp.hyperion.generationActivity.persistence.cancelled', severity: 'secondary' as const };
        }
        if (terminal?.type === 'ERROR') {
            return { labelKey: 'artemisApp.hyperion.generationActivity.persistence.failed', severity: 'danger' as const };
        }
        return undefined;
    });
    readonly canNavigateFileChanges = computed(() => {
        const terminalEvent = this.terminalEvent();
        return terminalEvent?.type === 'DONE' && terminalEvent.liveExerciseChanged === true;
    });
    readonly reviewJobId = computed(() => (this.canNavigateFileChanges() ? this.jobId() : this.revertAvailable() ? this.revertJobId() : undefined));
    readonly reviewTargets = computed<HyperionReviewTarget[]>(() => {
        if (!this.reviewJobId()) {
            return [];
        }
        const terminal = this.terminalEvent();
        const repositoryCommits = terminal?.savedRepositoryCommits;
        return [
            ...(terminal?.savedExerciseVersionId ? (['problem-statement'] as const) : []),
            ...(['solution', 'template', 'tests'] as const).filter((repository) => repositoryCommits?.[repository]),
        ];
    });

    /**
     * The one sentence a screen reader should hear about this run. Resolved to a string rather than kept as an
     * object so an unchanged status does not re-announce every time the facade polls.
     */
    private readonly liveStatusAnnouncement = computed(() => {
        const status = this.liveStatus();
        if (!status) {
            return '';
        }
        const headline = status.message ?? this.translateService.instant(status.labelKey);
        const persistence = status.busy ? undefined : this.persistenceState();
        return persistence ? `${headline} — ${this.translateService.instant(persistence.labelKey)}` : headline;
    });

    protected readonly faSpinner = faSpinner;
    protected readonly faRotateLeft = faRotateLeft;

    constructor() {
        this.facade.connect({ exerciseId: this.exerciseId, refreshingEditor: this.refreshingEditor });
        // The status region is rendered inside an @if that only becomes true in the same change-detection pass that
        // first fills it, so an in-template live region would miss the first status. The CDK announcer owns a region
        // that is mounted for the lifetime of the app, which is the only way the first announcement survives.
        effect(() => {
            const announcement = this.liveStatusAnnouncement();
            if (announcement) {
                void this.liveAnnouncer.announce(announcement, 'polite');
            }
        });
        this.facade.generationReverted.pipe(takeUntilDestroyed()).subscribe((completedAt) => this.generationReverted.emit(completedAt));
        this.facade.generationCompleted.pipe(takeUntilDestroyed()).subscribe((event) => this.generationCompleted.emit(event));
    }

    attachToJob(jobId: string, mode: HyperionGenerationMode): void {
        this.facade.attachToJob(jobId, mode);
    }

    confirmRevert(): void {
        this.facade.confirmRevert();
    }

    dismissRevert(): void {
        this.facade.dismissRevert();
    }

    acceptRevert(): void {
        this.facade.acceptRevert();
    }

    canNavigateFileChange(fileChange: ExerciseGenerationFileChange): boolean {
        return fileChange.action !== 'delete' && this.canNavigateFileChanges() && (fileChange.repo !== 'other' || fileChange.path === 'problem-statement.md');
    }

    selectFile(fileChange: ExerciseGenerationFileChange): void {
        if (this.canNavigateFileChange(fileChange)) {
            this.fileChangeSelected.emit(fileChange);
        }
    }

    requestReview(target: HyperionReviewTarget): void {
        const currentJobId = this.reviewJobId();
        if (!currentJobId || !this.reviewTargets().includes(target)) {
            return;
        }
        const terminal = this.terminalEvent();
        if (target === 'problem-statement' && terminal?.savedExerciseVersionId) {
            this.reviewRequested.emit({ target, jobId: currentJobId, savedExerciseVersionId: terminal.savedExerciseVersionId });
            return;
        }
        const commitHash = terminal?.savedRepositoryCommits?.[target];
        this.reviewRequested.emit(commitHash ? { target, jobId: currentJobId, commitHash } : { target, jobId: currentJobId });
    }

    requestStart(): void {
        if (this.startAllowed()) {
            this.startRequested.emit(undefined);
        }
    }

    runAgain(): void {
        if (this.canRunAgain()) {
            this.startRequested.emit(this.mode());
        }
    }

    cancel(): void {
        this.facade.cancel();
    }

    retryStatus(): void {
        this.facade.retryStatus();
    }
}
