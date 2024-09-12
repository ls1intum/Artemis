import { Component, OnDestroy, OnInit } from '@angular/core';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { Subscription, throwError } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { CommitInfo } from 'app/entities/programming/programming-submission.model';
import dayjs from 'dayjs/esm';
import { catchError, map, tap } from 'rxjs/operators';

@Component({
    selector: 'jhi-commit-details-view',
    templateUrl: './commit-details-view.component.html',
})
export class CommitDetailsViewComponent implements OnDestroy, OnInit {
    report: ProgrammingExerciseGitDiffReport;
    exerciseId: number;
    participationId?: number;
    commitHash: string;
    isTemplate = false;

    errorWhileFetching = false;
    leftCommitFileContentByPath: Map<string, string>;
    rightCommitFileContentByPath: Map<string, string>;
    commits: CommitInfo[] = [];
    currentCommit: CommitInfo;
    previousCommit: CommitInfo;
    repositoryType?: string;

    repoFilesSubscription: Subscription;
    participationRepoFilesAtLeftCommitSubscription: Subscription;
    participationRepoFilesAtRightCommitSubscription: Subscription;

    paramSub: Subscription;
    participationSub: Subscription;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private route: ActivatedRoute,
    ) {}

    ngOnDestroy(): void {
        this.repoFilesSubscription?.unsubscribe();
        this.participationRepoFilesAtLeftCommitSubscription?.unsubscribe();
        this.participationRepoFilesAtRightCommitSubscription?.unsubscribe();
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
            this.participationId = isNaN(Number(params['participationId'])) ? undefined : Number(params['participationId']);
            this.commitHash = params['commitHash'];
            this.repositoryType = params['repositoryType'] || undefined;
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
        if (this.repositoryType) {
            commitInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitHistoryForTemplateSolutionOrTests(this.exerciseId, this.repositoryType);
        } else if (this.participationId) {
            commitInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitHistoryForParticipation(this.participationId);
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
                next: () => this.getDiffReport(),
                error: () => {
                    this.errorWhileFetching = true;
                },
            });
    }

    /**
     * Gets the diff report for the current and previous commit or the template commit and an empty file.
     * @private
     */
    private getDiffReport() {
        this.repoFilesSubscription = this.programmingExerciseService
            .getDiffReportForCommits(this.exerciseId, this.participationId, this.previousCommit.hash!, this.currentCommit.hash!, this.repositoryType)
            .subscribe((report) => {
                this.handleNewReport(report!);
            });
    }

    /**
     * Handles the new report and sets the report, the left and right commit hash and the participation ids for the left and right commit.
     * @param report the new report
     * @private
     */
    private handleNewReport(report: ProgrammingExerciseGitDiffReport) {
        this.report = report;
        this.report.leftCommitHash = this.previousCommit.hash;
        this.report.rightCommitHash = this.currentCommit.hash;
        this.report.participationIdForLeftCommit = this.participationId;
        this.report.participationIdForRightCommit = this.participationId;
        this.fetchParticipationRepoFiles();
    }

    /**
     * Fetches the participation repository files for the left and right commit.
     * @private
     */
    private fetchParticipationRepoFiles() {
        if (this.isTemplate) {
            this.leftCommitFileContentByPath = new Map<string, string>();
            this.fetchParticipationRepoFilesAtRightCommit();
        } else {
            this.participationRepoFilesAtLeftCommitSubscription = this.programmingExerciseParticipationService
                .getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(
                    this.exerciseId,
                    this.report.participationIdForLeftCommit!,
                    this.report.leftCommitHash!,
                    this.repositoryType,
                )
                .subscribe({
                    next: (filesWithContent: Map<string, string>) => {
                        this.leftCommitFileContentByPath = filesWithContent;
                        this.fetchParticipationRepoFilesAtRightCommit();
                    },
                    error: () => {
                        this.errorWhileFetching = true;
                    },
                });
        }
    }

    /**
     * Fetches the participation repository files for the right commit.
     * @private
     */
    private fetchParticipationRepoFilesAtRightCommit() {
        this.participationRepoFilesAtRightCommitSubscription = this.programmingExerciseParticipationService
            .getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(
                this.exerciseId,
                this.report.participationIdForRightCommit!,
                this.report.rightCommitHash!,
                this.repositoryType,
            )
            .subscribe({
                next: (filesWithContent: Map<string, string>) => {
                    this.rightCommitFileContentByPath = filesWithContent;
                },
                error: () => {
                    this.errorWhileFetching = true;
                },
            });
    }
}
