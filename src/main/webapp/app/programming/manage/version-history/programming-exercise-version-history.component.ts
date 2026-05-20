import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { ExerciseSnapshotDTO } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';
import { ExerciseVersionMetadata } from 'app/exercise/version-history/shared/exercise-version-history.model';
import { ExerciseVersionHistoryService } from 'app/exercise/version-history/shared/exercise-version-history.service';
import { ExerciseVersionHistoryLayoutComponent } from 'app/exercise/version-history/shared/exercise-version-history-layout.component';
import { ExerciseVersionHistoryTimelineComponent } from 'app/exercise/version-history/shared/exercise-version-history-timeline.component';
import { ExerciseVersionSharedSnapshotMetadataComponent } from 'app/exercise/version-history/shared/exercise-version-shared-snapshot-metadata.component';
import { ProgrammingExerciseVersionProgrammingMetadataComponent } from 'app/programming/manage/version-history/programming-exercise-version-programming-metadata.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { Subscription, finalize } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-version-history',
    templateUrl: './programming-exercise-version-history.component.html',
    styleUrls: ['./programming-exercise-version-history.component.scss'],
    imports: [
        TranslateDirective,
        MessageModule,
        SkeletonModule,
        ExerciseVersionHistoryLayoutComponent,
        ExerciseVersionHistoryTimelineComponent,
        ExerciseVersionSharedSnapshotMetadataComponent,
        ProgrammingExerciseVersionProgrammingMetadataComponent,
    ],
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
export class ProgrammingExerciseVersionHistoryComponent implements OnInit, OnDestroy {
    private readonly route = inject(ActivatedRoute);
    private readonly versionHistoryService = inject(ExerciseVersionHistoryService);
    private readonly destroyRef = inject(DestroyRef);

    /** Subscription for the in-flight snapshot request, cancelled when a new version is selected. */
    private snapshotSubscription?: Subscription;

    private readonly pageSize = 20;

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
    /** Full snapshot for the selected version, fetched on demand. */
    readonly selectedSnapshot = signal<ExerciseSnapshotDTO | undefined>(undefined);

    /** `true` while the initial page of versions is being fetched. */
    readonly isLoadingVersions = signal(false);
    /** `true` while an additional page of versions is being fetched. */
    readonly isLoadingMoreVersions = signal(false);
    /** `true` while the snapshot for the selected version is being fetched. */
    readonly isLoadingSnapshot = signal(false);

    /** i18n key for timeline-level errors, or `undefined` if none. */
    readonly timelineError = signal<string | undefined>(undefined);
    /** i18n key for snapshot-level errors, or `undefined` if none. */
    readonly snapshotError = signal<string | undefined>(undefined);

    /** Whether there are more pages of versions available on the server. */
    readonly hasMore = computed(() => this.nextPage() !== undefined);

    ngOnDestroy(): void {
        this.snapshotSubscription?.unsubscribe();
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
    }

    /** Selects a version and loads its snapshot. No-ops if already selected unless the previous fetch failed. */
    onSelectVersion(versionId: number): void {
        if (this.selectedVersionId() === versionId && (this.isLoadingSnapshot() || this.selectedSnapshot() !== undefined)) {
            return;
        }

        this.selectedVersionId.set(versionId);
        this.selectedSnapshot.set(undefined);
        this.snapshotError.set(undefined);
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
                            this.selectedSnapshot.set(undefined);
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
    private loadSnapshot(versionId: number): void {
        const exerciseId = this.exerciseId();
        if (!exerciseId) {
            return;
        }

        // Cancel any in-flight snapshot request to avoid spinner race conditions
        this.snapshotSubscription?.unsubscribe();

        this.isLoadingSnapshot.set(true);
        this.snapshotSubscription = this.versionHistoryService
            .getSnapshot(exerciseId, versionId)
            .pipe(finalize(() => this.isLoadingSnapshot.set(false)))
            .subscribe({
                next: (snapshot) => {
                    this.selectedSnapshot.set(snapshot);
                },
                error: () => {
                    this.snapshotError.set('artemisApp.exercise.versionHistory.errors.snapshotLoadFailed');
                },
            });
    }
}
