import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseSnapshotDTO } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';
import { ExerciseVersionMetadata } from 'app/exercise/version-history/shared/exercise-version-history.model';
import { ExerciseVersionHistoryService } from 'app/exercise/version-history/shared/exercise-version-history.service';
import { ExerciseVersionHistoryLayoutComponent } from 'app/exercise/version-history/shared/exercise-version-history-layout.component';
import { ExerciseVersionHistoryTimelineComponent } from 'app/exercise/version-history/shared/exercise-version-history-timeline.component';
import { ExerciseVersionSharedSnapshotMetadataComponent } from 'app/exercise/version-history/shared/exercise-version-shared-snapshot-metadata.component';
import { getRevertConfig } from 'app/exercise/version-history/shared/revert-field.registry';
import { VersionHistoryViewMode, booleanLabel } from 'app/exercise/version-history/shared/version-history.utils';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseVersionProgrammingMetadataComponent } from 'app/programming/manage/version-history/programming-exercise-version-programming-metadata.component';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { cloneDeep } from 'lodash-es';
import dayjs from 'dayjs/esm';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { Tab, TabList, Tabs } from 'primeng/tabs';
import { ConfirmationService } from 'primeng/api';
import { finalize } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-version-history',
    templateUrl: './programming-exercise-version-history.component.html',
    styleUrls: ['./programming-exercise-version-history.component.scss'],
    imports: [
        TranslateDirective,
        ConfirmDialogModule,
        MessageModule,
        SkeletonModule,
        Tabs,
        TabList,
        Tab,
        ExerciseVersionHistoryLayoutComponent,
        ExerciseVersionHistoryTimelineComponent,
        ExerciseVersionSharedSnapshotMetadataComponent,
        ProgrammingExerciseVersionProgrammingMetadataComponent,
    ],
    providers: [ConfirmationService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
/**
 * Smart (container) component for the programming exercise version history page.
 *
 * Orchestrates fetching paginated version metadata and on-demand snapshots,
 * then delegates rendering to presentational child components:
 * - {@link ExerciseVersionHistoryTimelineComponent} — version timeline sidebar
 * - {@link ExerciseVersionSharedSnapshotMetadataComponent} — exercise-agnostic metadata
 * - {@link ProgrammingExerciseVersionProgrammingMetadataComponent} — programming-specific metadata
 */
export class ProgrammingExerciseVersionHistoryComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly versionHistoryService = inject(ExerciseVersionHistoryService);
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly confirmationService = inject(ConfirmationService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    private readonly pageSize = 20;
    private snapshotRequestToken = 0;

    /** Parsed exercise id from the route, set once in {@link ngOnInit}. */
    readonly exerciseId = signal<number | undefined>(undefined);
    /** Accumulated version metadata entries loaded so far (across all pages). */
    readonly versions = signal<ExerciseVersionMetadata[]>([]);
    /** Server-reported total number of versions. */
    readonly totalItems = signal(0);
    /** Zero-based index of the next page to fetch, or `undefined` if all pages are loaded. */
    readonly nextPage = signal<number | undefined>(undefined);
    /** Id of the version currently selected in the timeline. */
    readonly selectedVersionId = signal<number | undefined>(undefined);
    /** Snapshot cache, keyed by version id. */
    readonly snapshotCache = signal<Record<number, ExerciseSnapshotDTO>>({});
    /** Per-version loading flags for snapshot requests. */
    readonly loadingSnapshots = signal<Record<number, boolean>>({});
    /** Full snapshot for the selected version, read from the cache. */
    readonly selectedSnapshot = computed(() => {
        const selectedVersionId = this.selectedVersionId();
        return selectedVersionId ? this.snapshotCache()[selectedVersionId] : undefined;
    });
    /** Version id immediately preceding the selected version, when already loaded. */
    readonly previousVersionId = computed(() => {
        const selectedVersionId = this.selectedVersionId();
        if (!selectedVersionId) {
            return undefined;
        }

        const versions = this.versions();
        const index = versions.findIndex((version) => version.id === selectedVersionId);
        if (index === -1 || index >= versions.length - 1) {
            return undefined;
        }

        return versions[index + 1].id;
    });
    /** Cached snapshot for the immediately previous version. */
    readonly previousSnapshot = computed(() => {
        const previousVersionId = this.previousVersionId();
        return previousVersionId ? this.snapshotCache()[previousVersionId] : undefined;
    });

    /** `true` while the initial page of versions is being fetched. */
    readonly isLoadingVersions = signal(false);
    /** `true` while an additional page of versions is being fetched. */
    readonly isLoadingMoreVersions = signal(false);
    /** `true` while the snapshot for the selected version is being fetched. */
    readonly isLoadingSnapshot = computed(() => {
        const selectedVersionId = this.selectedVersionId();
        return selectedVersionId ? this.loadingSnapshots()[selectedVersionId] === true : false;
    });
    /** `true` while the snapshot for the selected version's predecessor is being fetched. */
    readonly isLoadingPreviousSnapshot = computed(() => {
        const previousVersionId = this.previousVersionId();
        return previousVersionId ? this.loadingSnapshots()[previousVersionId] === true : false;
    });

    /** i18n key for timeline-level errors, or `undefined` if none. */
    readonly timelineError = signal<string | undefined>(undefined);
    /** i18n key for snapshot-level errors, or `undefined` if none. */
    readonly snapshotError = signal<string | undefined>(undefined);
    /** Error message for diff-base snapshot loading, or `undefined` if none. */
    readonly diffBaseError = signal<string | undefined>(undefined);
    /** Currently selected snapshot view. */
    readonly viewMode = signal<VersionHistoryViewMode>('full');
    /** The current exercise entity, fetched for revert operations. */
    readonly currentExercise = signal<ProgrammingExercise | undefined>(undefined);
    /** Whether a revert operation is in progress. */
    readonly isReverting = signal(false);

    /** Whether there are more pages of versions available on the server. */
    readonly hasMore = computed(() => this.nextPage() !== undefined);
    /** Whether the selected version may have a predecessor, even if it is not loaded yet. */
    readonly showDiffTab = computed(() => {
        const selectedVersionId = this.selectedVersionId();
        if (!selectedVersionId) {
            return false;
        }

        const versions = this.versions();
        const index = versions.findIndex((version) => version.id === selectedVersionId);
        if (index === -1) {
            return false;
        }

        return index < versions.length - 1 || this.hasMore();
    });
    /** Whether the currently selected version can already be compared to a loaded predecessor snapshot. */
    readonly canShowDiff = computed(() => this.previousVersionId() !== undefined);

    constructor() {
        effect(() => {
            const selectedVersionId = this.selectedVersionId();
            if (!selectedVersionId) {
                return;
            }

            this.ensurePredecessorLoaded(selectedVersionId);
        });

        effect(() => {
            if (!this.showDiffTab() && this.viewMode() === 'changes') {
                this.viewMode.set('full');
            }
        });

        effect(() => {
            const viewMode = this.viewMode();
            const previousVersionId = this.previousVersionId();
            if (viewMode !== 'changes' || !previousVersionId) {
                return;
            }

            this.loadSnapshot(previousVersionId, 'previous');
        });
    }

    /** Parses the exercise id from the route and triggers the initial version load. */
    ngOnInit(): void {
        const exerciseIdParam = this.route.snapshot.paramMap.get('exerciseId');
        const exerciseId = exerciseIdParam ? Number(exerciseIdParam) : undefined;
        if (!exerciseId) {
            this.timelineError.set('artemisApp.exercise.versionHistory.errors.invalidExerciseId');
            return;
        }

        this.exerciseId.set(exerciseId);
        this.loadVersions(0, true);
        this.loadExercise(exerciseId);
    }

    /** Selects a version and loads its snapshot. No-ops if already selected unless the previous fetch failed. */
    onSelectVersion(versionId: number): void {
        if (this.selectedVersionId() === versionId && (this.isLoadingSnapshot() || this.selectedSnapshot() !== undefined)) {
            return;
        }

        this.selectedVersionId.set(versionId);
        this.snapshotError.set(undefined);
        this.diffBaseError.set(undefined);
        this.viewMode.set('changes');
        this.loadSnapshot(versionId);
    }

    /** Loads the next page of versions if one is available. */
    onLoadMoreVersions(): void {
        const page = this.nextPage();
        if (page === undefined) {
            return;
        }

        this.loadVersions(page, false);
    }

    /** Switches between full snapshot and diff view tabs. */
    onSelectViewMode(viewMode: VersionHistoryViewMode): void {
        if (viewMode === 'changes' && !this.showDiffTab()) {
            return;
        }

        this.viewMode.set(viewMode);
        this.diffBaseError.set(undefined);
    }

    onTabValueChange(value: string | number | undefined): void {
        if (value === 'full' || value === 'changes') {
            this.onSelectViewMode(value);
        }
    }

    /**
     * Handles a revert request emitted by a presentational metadata child component.
     *
     * Looks up the field in the revert registry, reads the exercise's actual current
     * value for the confirmation dialog, then opens a PrimeNG ConfirmDialog.
     * On acceptance the appropriate update endpoint is called via {@link executeRevert}.
     */
    onRevertField(event: { fieldId: string; fieldLabel: string; previousRaw: unknown }): void {
        const config = getRevertConfig(event.fieldId);
        if (!config) {
            return;
        }

        const exercise = this.currentExercise();
        if (!exercise) {
            this.alertService.error('artemisApp.exercise.versionHistory.revert.error');
            return;
        }

        const fieldLabel = event.fieldLabel;
        const header = this.translateService.instant('artemisApp.exercise.versionHistory.revert.confirmHeader');

        let message: string;
        if (config.updateStrategy === 'problemStatement') {
            message = this.translateService.instant('artemisApp.exercise.versionHistory.revert.confirmMessageProblemStatement');
        } else {
            const targetDisplay = this.formatDisplayValue(event.previousRaw);
            const currentValue = this.getNestedValue(exercise, config.entityPath);
            const currentDisplay = this.formatDisplayValue(currentValue);
            message = this.translateService.instant('artemisApp.exercise.versionHistory.revert.confirmMessage', {
                field: fieldLabel,
                currentValue: currentDisplay,
                targetValue: targetDisplay,
            });
        }

        this.confirmationService.confirm({
            key: 'revert-field',
            header,
            message,
            accept: () => this.executeRevert(event.fieldId, fieldLabel, event.previousRaw, config, exercise),
        });
    }

    /**
     * Performs the actual revert by calling the appropriate backend endpoint.
     *
     * For `problemStatement` fields, uses the dedicated PATCH endpoint.
     * For all other fields, clones the current exercise entity, patches the
     * single field at the configured entity path, and calls the full update
     * or timeline update endpoint.
     *
     * On success, invalidates the snapshot cache and reloads both the exercise
     * entity and the version timeline so the UI reflects the new state.
     */
    private executeRevert(fieldId: string, fieldLabel: string, previousRaw: unknown, config: NonNullable<ReturnType<typeof getRevertConfig>>, exercise: ProgrammingExercise): void {
        const exerciseId = this.exerciseId();
        if (!exerciseId) {
            return;
        }

        this.isReverting.set(true);

        if (config.updateStrategy === 'problemStatement') {
            const problemStatement = previousRaw as string | undefined;
            this.programmingExerciseService
                .updateProblemStatement(exerciseId, problemStatement)
                .pipe(finalize(() => this.isReverting.set(false)))
                .subscribe({
                    next: () => this.onRevertSuccess(fieldLabel, exerciseId),
                    error: () => this.alertService.error('artemisApp.exercise.versionHistory.revert.error', { field: fieldLabel }),
                });
            return;
        }

        const clone = cloneDeep(exercise);
        const convertedValue = this.convertValue(previousRaw, config.valueType);
        this.setNestedValue(clone, config.entityPath, convertedValue);

        const update$ = config.updateStrategy === 'timeline' ? this.programmingExerciseService.updateTimeline(clone) : this.programmingExerciseService.update(clone);

        update$.pipe(finalize(() => this.isReverting.set(false))).subscribe({
            next: () => this.onRevertSuccess(fieldLabel, exerciseId),
            error: () => this.alertService.error('artemisApp.exercise.versionHistory.revert.error', { field: fieldLabel }),
        });
    }

    /** Invalidates caches and reloads state after a successful revert. */
    private onRevertSuccess(fieldLabel: string, exerciseId: number): void {
        this.alertService.success('artemisApp.exercise.versionHistory.revert.success', { field: fieldLabel });
        this.snapshotCache.set({});
        this.loadExercise(exerciseId);
        this.loadVersions(0, true);
    }

    /** Fetches the full exercise entity (with plagiarism config) for use by the revert logic. */
    private loadExercise(exerciseId: number): void {
        this.programmingExerciseService.find(exerciseId, true).subscribe({
            next: (response) => {
                if (response.body) {
                    this.currentExercise.set(response.body);
                }
            },
            error: () => {
                this.alertService.error('artemisApp.exercise.versionHistory.errors.exerciseLoadFailed');
            },
        });
    }

    /** Formats a raw field value for display in the confirmation dialog. */
    private formatDisplayValue(value: unknown): string {
        if (value === undefined || value === null) {
            return '-';
        }
        if (typeof value === 'boolean') {
            return booleanLabel(this.translateService, value) ?? '-';
        }
        return String(value);
    }

    /** Converts a snapshot raw value to the type expected by the exercise entity (e.g. ISO string → dayjs for dates). */
    private convertValue(raw: unknown, valueType: string): unknown {
        if (raw === undefined || raw === null) {
            return undefined;
        }
        switch (valueType) {
            case 'date':
                return typeof raw === 'string' ? dayjs(raw) : undefined;
            case 'number':
                return typeof raw === 'number' ? raw : Number(raw);
            case 'boolean':
                return typeof raw === 'boolean' ? raw : raw === 'true';
            default:
                return raw;
        }
    }

    /** Reads a deeply nested property from an object using a dot-separated path. */
    private getNestedValue(obj: object, path: string): unknown {
        return path.split('.').reduce<unknown>((current, key) => {
            if (typeof current === 'object' && current !== null) {
                return (current as Record<string, unknown>)[key];
            }
            return undefined;
        }, obj);
    }

    /** Sets a deeply nested property on an object using a dot-separated path, creating intermediate objects as needed. */
    private setNestedValue(obj: object, path: string, value: unknown): void {
        const keys = path.split('.');
        if (keys.some((key) => key === '__proto__' || key === 'constructor' || key === 'prototype')) {
            return;
        }
        let current: Record<string, unknown> = obj as Record<string, unknown>;
        for (let i = 0; i < keys.length - 1; i++) {
            if (current[keys[i]] === undefined || current[keys[i]] === null || typeof current[keys[i]] !== 'object') {
                current[keys[i]] = {};
            }
            current = current[keys[i]] as Record<string, unknown>;
        }
        current[keys[keys.length - 1]] = value;
    }

    /**
     * Fetches a page of version metadata from the server.
     *
     * No explicit subscription cleanup is needed because HTTP observables
     * complete after a single emission. Updating signals in the callbacks
     * after the component is destroyed is harmless — signals do not throw.
     *
     * @param page  zero-based page index to fetch
     * @param reset if `true`, replaces the current list and auto-selects the newest version;
     *              if `false`, appends to the existing list (load-more behaviour)
     */
    private loadVersions(page: number, reset: boolean): void {
        const exerciseId = this.exerciseId();
        if (!exerciseId) {
            return;
        }

        this.timelineError.set(undefined);
        this.isLoadingVersions.set(reset);
        this.isLoadingMoreVersions.set(!reset);

        this.versionHistoryService
            .getVersions(exerciseId, page, this.pageSize)
            .pipe(
                takeUntilDestroyed(this.destroyRef),
                finalize(() => {
                    this.isLoadingVersions.set(false);
                    this.isLoadingMoreVersions.set(false);
                }),
            )
            .subscribe({
                next: ({ versions, totalItems, nextPage }) => {
                    this.totalItems.set(totalItems);
                    this.nextPage.set(nextPage);
                    this.versions.set(reset ? versions : [...this.versions(), ...versions]);

                    if (reset) {
                        if (versions.length > 0) {
                            this.onSelectVersion(versions[0].id);
                        } else {
                            this.selectedVersionId.set(undefined);
                        }
                    }
                },
                error: () => {
                    this.timelineError.set('artemisApp.exercise.versionHistory.errors.timelineLoadFailed');
                },
            });
    }

    /**
     * Fetches the full snapshot for the given version.
     *
     * Any in-flight snapshot request is cancelled before starting a new one,
     * preventing spinner race conditions when the user rapidly switches versions.
     *
     * @param versionId the version whose snapshot to fetch
     */
    private loadSnapshot(versionId: number, target: 'selected' | 'previous' = 'selected'): void {
        const exerciseId = this.exerciseId();
        if (!exerciseId || this.snapshotCache()[versionId] || this.loadingSnapshots()[versionId]) {
            return;
        }

        const requestToken = ++this.snapshotRequestToken;
        this.setSnapshotLoading(versionId, true);

        this.versionHistoryService
            .getSnapshot(exerciseId, versionId)
            .pipe(finalize(() => this.setSnapshotLoading(versionId, false)))
            .subscribe({
                next: (snapshot) => {
                    if (requestToken !== this.snapshotRequestToken && target === 'selected' && this.selectedVersionId() !== versionId) {
                        return;
                    }
                    this.snapshotCache.update((snapshotCache) => {
                        const nextCache: Record<number, ExerciseSnapshotDTO> = {};
                        for (const key in snapshotCache) {
                            if (Object.prototype.hasOwnProperty.call(snapshotCache, key)) {
                                nextCache[key] = snapshotCache[key];
                            }
                        }
                        nextCache[versionId] = snapshot;
                        return nextCache;
                    });
                },
                error: () => {
                    if (target === 'selected') {
                        if (this.selectedVersionId() === versionId) {
                            this.snapshotError.set('artemisApp.exercise.versionHistory.errors.snapshotLoadFailed');
                        }
                        return;
                    }

                    // Guard against stale predecessor requests: if the user has switched
                    // versions since this request was issued, ignore the error.
                    if (requestToken !== this.snapshotRequestToken) {
                        return;
                    }

                    if (this.viewMode() === 'changes' && this.previousVersionId() === versionId) {
                        this.diffBaseError.set('artemisApp.exercise.versionHistory.errors.snapshotLoadFailed');
                    }
                },
            });
    }

    private ensurePredecessorLoaded(versionId: number): void {
        const versions = this.versions();
        const index = versions.findIndex((version) => version.id === versionId);
        if (index === -1 || index < versions.length - 1 || this.nextPage() === undefined || this.isLoadingMoreVersions()) {
            return;
        }

        this.loadVersions(this.nextPage()!, false);
    }

    private setSnapshotLoading(versionId: number, isLoading: boolean): void {
        this.loadingSnapshots.update((loadingSnapshots) => {
            const nextLoadingSnapshots: Record<number, boolean> = {};
            for (const key in loadingSnapshots) {
                if (Object.prototype.hasOwnProperty.call(loadingSnapshots, key)) {
                    nextLoadingSnapshots[key] = loadingSnapshots[key];
                }
            }
            if (!isLoading) {
                delete nextLoadingSnapshots[versionId];
            } else {
                nextLoadingSnapshots[versionId] = true;
            }
            return nextLoadingSnapshots;
        });
    }
}
