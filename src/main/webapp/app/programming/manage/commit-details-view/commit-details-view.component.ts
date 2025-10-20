import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { Subscription, forkJoin, of, throwError } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { CommitInfo } from 'app/programming/shared/entities/programming-submission.model';
import dayjs from 'dayjs/esm';
import { catchError, map, tap } from 'rxjs/operators';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { RepositoryDiffInformation, processRepositoryDiff } from 'app/programming/shared/utils/diff.utils';
@Component({
    selector: 'jhi-commit-details-view',
    templateUrl: './commit-details-view.component.html',
    imports: [GitDiffReportComponent, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class CommitDetailsViewComponent implements OnDestroy, OnInit {
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private route = inject(ActivatedRoute);
    private diffRunId = 0;

    exerciseId: number;
    repositoryId?: number; // acts as both participationId (USER repositories) and repositoryId (AUXILIARY repositories), undefined for TEMPLATE, SOLUTION and TEST
    commitHash: string;
    isTemplate = false;

    errorWhileFetching = false;
    leftCommitFileContentByPath: Map<string, string> = new Map();
    rightCommitFileContentByPath: Map<string, string> = new Map();
    repositoryDiffInformation: RepositoryDiffInformation;
    commits: CommitInfo[] = [];
    currentCommit: CommitInfo;
    previousCommit: CommitInfo;
    repositoryType: RepositoryType;
    diffReady = false;

    participationRepoFilesSubscription: Subscription;

    paramSub: Subscription;
    participationSub: Subscription;

    ngOnDestroy(): void {
        this.participationRepoFilesSubscription?.unsubscribe();
        this.paramSub?.unsubscribe();
        this.participationSub?.unsubscribe();
    }

    /**
     * On init, subscribe to the route params to get the exercise id, participation id and commit hash.
     * Then, retrieve the student participation with all results and handle the commits.
     * After that, retrieve and handle the commits.
     */
    ngOnInit(): void {
        this.paramSub = this.route.params.subscribe((params) => {
            this.exerciseId = Number(params['exerciseId']);
            this.repositoryId = Number(params['repositoryId']);
            this.commitHash = params['commitHash'];
            this.repositoryType = params['repositoryType'] ?? 'USER';
            this.retrieveAndHandleCommits();
        });
    }

    /**
     * Retrieves the commits for the participation and sets the current and previous commit.
     * If there is no previous commit, the template commit is chosen.
     * Finally the diff report is fetched.
     * @private
     */
    private retrieveAndHandleCommits() {
        let commitInfoSubscription;

        if (this.repositoryType === RepositoryType.TEMPLATE || this.repositoryType === RepositoryType.SOLUTION || this.repositoryType === RepositoryType.TESTS) {
            commitInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitHistoryForTemplateSolutionOrTests(this.exerciseId, this.repositoryType);
        }
        if (this.repositoryType === RepositoryType.AUXILIARY) {
            commitInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitHistoryForAuxiliaryRepository(this.exerciseId, this.repositoryId!);
        }
        if (this.repositoryType === RepositoryType.USER) {
            commitInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitHistoryForParticipation(this.repositoryId!);
        }
        if (!commitInfoSubscription) {
            return;
        }

        commitInfoSubscription
            .pipe(
                map((commits) => commits.sort((a, b) => (dayjs(b.timestamp).isAfter(dayjs(a.timestamp)) ? 1 : -1))),
                tap((sortedCommits) => {
                    this.commits = sortedCommits;
                    const foundIndex = this.commits.findIndex((commit) => commit.hash === this.commitHash);
                    if (foundIndex !== -1) {
                        this.currentCommit = this.commits[foundIndex];
                        this.previousCommit = foundIndex < this.commits.length - 1 ? this.commits[foundIndex + 1] : this.commits[this.commits.length - 1];
                        this.isTemplate = foundIndex === this.commits.length - 1;
                    }
                }),
                catchError(() => {
                    return throwError(() => new Error('Error processing commits'));
                }),
            )
            .subscribe({
                next: () => this.fetchParticipationRepoFiles(),
                error: () => {
                    this.errorWhileFetching = true;
                },
            });
    }

    /**
     * Fetches the participation repository files for both left and right commits in parallel.
     * @private
     */
    private fetchParticipationRepoFiles() {
        // Set ready state to false when starting diff processing
        this.diffReady = false;

        // Increment diff run sequence; used to ignore stale results from previous runs
        const runId = ++this.diffRunId;

        const leftCommitObservable = this.isTemplate
            ? of(new Map())
            : this.programmingExerciseParticipationService.getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(
                  this.exerciseId,
                  this.repositoryId!,
                  this.previousCommit.hash!,
                  this.repositoryType,
              );

        const rightCommitObservable = this.programmingExerciseParticipationService.getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(
            this.exerciseId,
            this.repositoryId!,
            this.currentCommit.hash!,
            this.repositoryType,
        );

        this.participationRepoFilesSubscription = forkJoin({
            leftFiles: leftCommitObservable,
            rightFiles: rightCommitObservable,
        }).subscribe({
            next: async (result) => {
                this.leftCommitFileContentByPath = result.leftFiles || new Map();
                this.rightCommitFileContentByPath = result.rightFiles || new Map();
                const diffInformation = await processRepositoryDiff(this.leftCommitFileContentByPath, this.rightCommitFileContentByPath);

                // Ignore stale results
                if (runId !== this.diffRunId) {
                    return;
                }

                this.repositoryDiffInformation = diffInformation;

                // Set ready state to true when diff processing is complete
                this.diffReady = true;
            },
            error: () => {
                // Ignore stale results
                if (runId !== this.diffRunId) {
                    return;
                }

                this.errorWhileFetching = true;
                this.leftCommitFileContentByPath = new Map();
                this.rightCommitFileContentByPath = new Map();
                this.diffReady = false;
            },
        });
    }
}
