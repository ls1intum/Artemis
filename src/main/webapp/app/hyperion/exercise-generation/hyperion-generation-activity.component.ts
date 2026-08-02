import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp, faCircleCheck, faCircleXmark, faRotateLeft, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HyperionGenerationActivityFacade, HyperionGenerationCompletedEvent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.facade';
import { displayFileChangePath, latestTerminalEvent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.utils';
import { ExerciseGenerationFileChange, HyperionFileChangeRepo, HyperionGenerationMode } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TumUiButtonDirective } from 'app/shared-ui/tum-ui/button/tum-ui-button.directive';
import { TumUiDialogComponent } from 'app/shared-ui/tum-ui/dialog/tum-ui-dialog.component';
import { TumUiMessageComponent } from 'app/shared-ui/tum-ui/message/tum-ui-message.component';
import { TumUiTagComponent } from 'app/shared-ui/tum-ui/tag/tum-ui-tag.component';
import { TumUiTooltipDirective } from 'app/shared-ui/tum-ui/tooltip/tum-ui-tooltip.directive';

const REPO_ORDER: HyperionFileChangeRepo[] = ['solution', 'template', 'tests', 'other'];

interface RepoFileGroup {
    repo: HyperionFileChangeRepo;
    files: ExerciseGenerationFileChange[];
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

@Component({
    selector: 'jhi-hyperion-generation-activity',
    templateUrl: './hyperion-generation-activity.component.html',
    styleUrl: './hyperion-generation-activity.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [HyperionGenerationActivityFacade],
    imports: [
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumUiButtonComponent,
        TumUiButtonDirective,
        TumUiDialogComponent,
        TumUiMessageComponent,
        TumUiTagComponent,
        TumUiTooltipDirective,
    ],
})
export class HyperionGenerationActivityComponent {
    private readonly facade = inject(HyperionGenerationActivityFacade);
    private readonly translateService = inject(TranslateService);

    readonly exerciseId = input<number | undefined>();
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
    readonly detailsExpanded = this.facade.detailsExpanded;
    readonly cancelRequested = this.facade.cancelRequested;
    readonly confirmRevertVisible = this.facade.confirmRevertVisible;
    readonly reverting = this.facade.reverting;
    readonly reverted = this.facade.reverted;
    readonly revertPartialRepositories = this.facade.revertPartialRepositories;

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
        const terminal = latestTerminalEvent(this.events());
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
    readonly recentEvents = computed(() =>
        this.events()
            .filter((event) => event.message)
            .slice(-8)
            .reverse(),
    );
    readonly currentProgress = computed(() => this.recentEvents()[0]);
    readonly previousProgress = computed(() => this.recentEvents().slice(1));
    readonly hasDetails = computed(() => this.fileChanges().length > 0 || this.previousProgress().length > 0);
    readonly detailsLabelKey = computed(() => {
        if (this.fileChanges().length) {
            return this.detailsExpanded() ? 'artemisApp.hyperion.generationActivity.hideChangedFiles' : 'artemisApp.hyperion.generationActivity.showChangedFiles';
        }
        return this.detailsExpanded() ? 'artemisApp.hyperion.generationActivity.hideDetails' : 'artemisApp.hyperion.generationActivity.showDetails';
    });
    readonly filesByRepo = computed<RepoFileGroup[]>(() => {
        const files = this.fileChanges();
        return REPO_ORDER.map((repo) => ({
            repo,
            files: files.filter((file) => file.repo === repo).sort((first, second) => this.displayPath(first).localeCompare(this.displayPath(second))),
        })).filter((group) => group.files.length > 0);
    });
    readonly liveStatus = computed<ActivityLiveStatus | undefined>(() => {
        if (this.refreshingEditor()) {
            return { labelKey: 'artemisApp.hyperion.generationActivity.refreshingEditor', busy: true };
        }
        if (this.editorRefreshFailed()) {
            return { labelKey: 'artemisApp.hyperion.generationActivity.editorRefreshFailed', busy: false };
        }
        const terminal = latestTerminalEvent(this.events());
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
        const type = latestTerminalEvent(this.events())?.type;
        if (type === 'CANCELLED') {
            return { labelKey: 'artemisApp.hyperion.generationActivity.terminalStatus.CANCELLED', severity: 'secondary' as const };
        }
        if (type === 'ERROR') {
            return { labelKey: 'artemisApp.hyperion.generationActivity.terminalStatus.ERROR', severity: 'danger' as const };
        }
        return undefined;
    });
    readonly terminalMessage = computed(() => latestTerminalEvent(this.events())?.message);
    readonly persistenceState = computed(() => {
        if (this.running()) {
            return { labelKey: 'artemisApp.hyperion.generationActivity.persistence.workingCopy', severity: 'warn' as const };
        }
        const terminal = latestTerminalEvent(this.events());
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
        const terminalEvent = latestTerminalEvent(this.events());
        return terminalEvent?.type === 'DONE' && terminalEvent.liveExerciseChanged === true;
    });
    readonly reviewJobId = computed(() => (this.canNavigateFileChanges() ? this.jobId() : this.revertAvailable() ? this.revertJobId() : undefined));
    readonly reviewTargets = computed<HyperionReviewTarget[]>(() => {
        if (!this.reviewJobId()) {
            return [];
        }
        const terminal = latestTerminalEvent(this.events());
        const repositoryCommits = terminal?.savedRepositoryCommits;
        return [
            ...(terminal?.savedExerciseVersionId ? (['problem-statement'] as const) : []),
            ...(['solution', 'template', 'tests'] as const).filter((repository) => repositoryCommits?.[repository]),
        ];
    });

    protected readonly faSpinner = faSpinner;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronUp = faChevronUp;
    protected readonly faCircleCheck = faCircleCheck;
    protected readonly faCircleXmark = faCircleXmark;
    protected readonly faRotateLeft = faRotateLeft;

    constructor() {
        effect(() => {
            this.facade.exerciseId.set(this.exerciseId());
            this.facade.refreshingEditor.set(this.refreshingEditor());
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

    toggleDetails(): void {
        this.facade.toggleDetails();
    }

    protected displayPath(fileChange: ExerciseGenerationFileChange): string {
        return displayFileChangePath(fileChange);
    }

    protected fileChangeAccessibleLabel(fileChange: ExerciseGenerationFileChange): string {
        const repository = this.translateService.instant(`artemisApp.hyperion.generationActivity.repo.${fileChange.repo}`);
        return `${repository}: ${this.displayPath(fileChange)}`;
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
        const terminal = latestTerminalEvent(this.events());
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
