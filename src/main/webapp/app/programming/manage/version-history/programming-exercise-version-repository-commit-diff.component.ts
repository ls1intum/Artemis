import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RepositoryDiffInformation, processRepositoryDiff } from 'app/programming/shared/utils/diff.utils';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { EMPTY, catchError, forkJoin, from, map, switchMap, tap } from 'rxjs';

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
                        <span jhiTranslate="artemisApp.programmingExercise.versionHistory.snapshot.repositoryDiffError"></span>
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
    imports: [GitDiffReportComponent, MessageModule, SkeletonModule, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgrammingExerciseVersionRepositoryCommitDiffComponent {
    private readonly participationService = inject(ProgrammingExerciseParticipationService);

    readonly exerciseId = input.required<number>();
    readonly repositoryType = input.required<RepositoryType>();
    readonly participationId = input<number | undefined>();
    readonly previousCommitId = input<string | undefined>();
    readonly currentCommitId = input<string | undefined>();

    readonly repositoryDiffInformation = signal<RepositoryDiffInformation | undefined>(undefined);
    readonly isLoading = signal(false);
    readonly error = signal(false);
    readonly shouldRender = computed(() => !!this.previousCommitId() && !!this.currentCommitId() && this.previousCommitId() !== this.currentCommitId());

    private readonly diffInputs = computed(() => ({
        exerciseId: this.exerciseId(),
        repositoryType: this.repositoryType(),
        participationId: this.participationId(),
        previousCommitId: this.previousCommitId(),
        currentCommitId: this.currentCommitId(),
    }));

    constructor() {
        toObservable(this.diffInputs)
            .pipe(
                takeUntilDestroyed(),
                tap(() => {
                    this.repositoryDiffInformation.set(undefined);
                    this.error.set(false);
                }),
                switchMap(({ exerciseId, repositoryType, participationId, previousCommitId, currentCommitId }) => {
                    if (!exerciseId || !repositoryType || !previousCommitId || !currentCommitId || previousCommitId === currentCommitId) {
                        this.isLoading.set(false);
                        return EMPTY;
                    }
                    this.isLoading.set(true);
                    return forkJoin({
                        previousFiles: this.loadFiles(exerciseId, participationId, repositoryType, previousCommitId),
                        currentFiles: this.loadFiles(exerciseId, participationId, repositoryType, currentCommitId),
                    }).pipe(
                        switchMap(({ previousFiles, currentFiles }) => from(processRepositoryDiff(previousFiles, currentFiles))),
                        catchError(() => {
                            this.error.set(true);
                            this.isLoading.set(false);
                            return EMPTY;
                        }),
                    );
                }),
            )
            .subscribe((repositoryDiffInformation) => {
                this.repositoryDiffInformation.set(repositoryDiffInformation);
                this.isLoading.set(false);
            });
    }

    private loadFiles(exerciseId: number, participationId: number | undefined, repositoryType: RepositoryType, commitId: string) {
        return this.participationService
            .getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(exerciseId, participationId, commitId, repositoryType)
            .pipe(map((files) => files ?? new Map<string, string>()));
    }
}
