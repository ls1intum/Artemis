import { Component, OnDestroy, OnInit } from '@angular/core';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { Subscription } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ActivatedRoute } from '@angular/router';
import { CommitInfo } from 'app/entities/programming-submission.model';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { tap } from 'rxjs/operators';

@Component({
    selector: 'jhi-commit-details-view',
    templateUrl: './commit-details-view.component.html',
})
export class CommitDetailsViewComponent implements OnDestroy, OnInit {
    report: ProgrammingExerciseGitDiffReport;
    exerciseId: number;
    participationId: number;
    commitHash: string;
    isTemplate = false;

    errorWhileFetchingRepos = false;
    leftCommitFileContentByPath: Map<string, string>;
    rightCommitFileContentByPath: Map<string, string>;
    commitsInfoSubscription: Subscription;
    commits: CommitInfo[] = [];
    currentCommit: CommitInfo;
    previousCommit: CommitInfo;
    studentParticipation: ProgrammingExerciseStudentParticipation;

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
        this.commitsInfoSubscription?.unsubscribe();
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
            this.participationId = Number(params['participationId']);
            this.commitHash = params['commitHash'];
            this.participationSub = this.programmingExerciseParticipationService.getStudentParticipationWithAllResults(this.participationId).subscribe((participation) => {
                this.studentParticipation = participation;
                this.retrieveAndHandleCommits();
            });
        });
    }

    /**
     * Retrieves the commits for the participation and sets the current and previous commit.
     * If there is no previous commit, the template commit is chosen.
     * Finally the diff report is fetched.
     * @private
     */
    private retrieveAndHandleCommits() {
        this.commitsInfoSubscription = this.programmingExerciseParticipationService
            .retrieveCommitHistoryForParticipation(this.participationId)
            .pipe(
                tap((commits) => {
                    this.commits = commits.sort((a, b) => (dayjs(b.timestamp!).isAfter(dayjs(a.timestamp!)) ? 1 : -1));
                    for (let i = 0; i < this.commits.length; i++) {
                        const commit = this.commits[i];
                        if (commit.hash === this.commitHash) {
                            this.currentCommit = commit;
                            if (i < this.commits.length - 1) {
                                this.previousCommit = this.commits[i + 1];
                            } else {
                                // choose template commit
                                this.isTemplate = true;
                                this.previousCommit = this.commits[this.commits.length - 1];
                            }
                            break;
                        }
                    }
                }),
            )
            .subscribe({
                next: () => {
                    this.getDiffReport();
                },
            });
    }

    /**
     * Gets the diff report for the current and previous commit or the template commit and an empty file.
     * @private
     */
    private getDiffReport() {
        this.repoFilesSubscription = this.programmingExerciseService
            .getDiffReportForCommits(this.exerciseId, this.participationId, this.previousCommit.hash!, this.currentCommit.hash!)
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
        this.report.programmingExercise = this.studentParticipation.exercise as ProgrammingExercise;
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
                .getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(this.report.participationIdForLeftCommit!, this.report.leftCommitHash!)
                .subscribe({
                    next: (filesWithContent: Map<string, string>) => {
                        this.leftCommitFileContentByPath = filesWithContent;
                        this.fetchParticipationRepoFilesAtRightCommit();
                    },
                    error: () => {
                        this.errorWhileFetchingRepos = true;
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
            .getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(this.report.participationIdForRightCommit!, this.report.rightCommitHash!)
            .subscribe({
                next: (filesWithContent: Map<string, string>) => {
                    this.rightCommitFileContentByPath = filesWithContent;
                },
                error: () => {
                    this.errorWhileFetchingRepos = true;
                },
            });
    }
}
