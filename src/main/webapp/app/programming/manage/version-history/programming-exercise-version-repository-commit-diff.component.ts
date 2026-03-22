import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { RepositoryDiffInformation, processRepositoryDiff } from 'app/programming/shared/utils/diff.utils';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { forkJoin, from, map, switchMap } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-version-repository-commit-diff',
    template: `
        @if (shouldRender()) {
            @if (isLoading()) {
                <div class="repository-commit-diff">
                    <p-skeleton width="100%" height="12rem" />
                </div>
            } @else if (error()) {
                <p-message severity="warn" styleClass="repository-commit-diff__message">
                    <ng-template pTemplate>
                        <span>Repository diff could not be loaded.</span>
                    </ng-template>
                </p-message>
            } @else if (repositoryDiffInformation(); as repositoryDiffInformation) {
                <div class="repository-commit-diff">
                    <jhi-git-diff-report
                        [repositoryDiffInformation]="repositoryDiffInformation"
                        [isRepositoryView]="true"
                        [diffForTemplateAndSolution]="false"
                        [leftCommitHash]="previousCommitId()"
                        [rightCommitHash]="currentCommitId()"
                        [participationId]="participationId()"
                    />
                </div>
            }
        }
    `,
    styles: [
        `
            .repository-commit-diff,
            .repository-commit-diff__message {
                margin-top: 0.85rem;
            }
        `,
    ],
    imports: [GitDiffReportComponent, MessageModule, SkeletonModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgrammingExerciseVersionRepositoryCommitDiffComponent {
    private readonly participationService = inject(ProgrammingExerciseParticipationService);
    private readonly destroyRef = inject(DestroyRef);

    readonly exerciseId = input.required<number>();
    readonly repositoryType = input.required<RepositoryType>();
    readonly participationId = input<number | undefined>();
    readonly previousCommitId = input<string | undefined>();
    readonly currentCommitId = input<string | undefined>();

    readonly repositoryDiffInformation = signal<RepositoryDiffInformation | undefined>(undefined);
    readonly isLoading = signal(false);
    readonly error = signal(false);
    readonly shouldRender = computed(() => !!this.previousCommitId() && !!this.currentCommitId() && this.previousCommitId() !== this.currentCommitId());

    private requestId = 0;

    constructor() {
        effect(() => {
            const exerciseId = this.exerciseId();
            const repositoryType = this.repositoryType();
            const participationId = this.participationId();
            const previousCommitId = this.previousCommitId();
            const currentCommitId = this.currentCommitId();

            this.repositoryDiffInformation.set(undefined);
            this.error.set(false);

            if (!exerciseId || !repositoryType || !previousCommitId || !currentCommitId || previousCommitId === currentCommitId) {
                this.isLoading.set(false);
                return;
            }

            const currentRequestId = ++this.requestId;
            this.isLoading.set(true);

            forkJoin({
                previousFiles: this.loadFiles(exerciseId, participationId, repositoryType, previousCommitId),
                currentFiles: this.loadFiles(exerciseId, participationId, repositoryType, currentCommitId),
            })
                .pipe(
                    takeUntilDestroyed(this.destroyRef),
                    switchMap(({ previousFiles, currentFiles }) => from(processRepositoryDiff(previousFiles, currentFiles))),
                )
                .subscribe({
                    next: (repositoryDiffInformation) => {
                        if (currentRequestId !== this.requestId) {
                            return;
                        }
                        this.repositoryDiffInformation.set(repositoryDiffInformation);
                        this.isLoading.set(false);
                    },
                    error: () => {
                        if (currentRequestId !== this.requestId) {
                            return;
                        }
                        this.error.set(true);
                        this.isLoading.set(false);
                    },
                });
        });
    }

    private loadFiles(exerciseId: number, participationId: number | undefined, repositoryType: RepositoryType, commitId: string) {
        return this.participationService
            .getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(exerciseId, participationId, commitId, repositoryType)
            .pipe(map((files) => files ?? new Map<string, string>()));
    }
}
