import { Component } from '@angular/core';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { Subscription } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ActivatedRoute } from '@angular/router';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming-submission.model';
import dayjs from 'dayjs';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-commit-details-view',
    templateUrl: './commit-details-view.component.html',
    styleUrl: './commit-details-view.component.scss',
})
export class CommitDetailsViewComponent {
    exercise: ProgrammingExercise;
    report: ProgrammingExerciseGitDiffReport;
    diffForTemplateAndSolution = false;
    exerciseId: number;
    participationId: number;
    commitHash: string;

    errorWhileFetchingRepos = false;
    leftCommitFileContentByPath: Map<string, string>;
    rightCommitFileContentByPath: Map<string, string>;
    commitsInfoSubscription: Subscription;
    commits: CommitInfo[] = [];
    currentSubmission: ProgrammingSubmission;
    previousSubmission: ProgrammingSubmission;
    currentCommit: CommitInfo;
    previousCommit: CommitInfo;
    user: User;

    private templateRepoFilesSubscription: Subscription;
    private solutionRepoFilesSubscription: Subscription;
    private participationRepoFilesAtLeftCommitSubscription: Subscription;
    private participationRepoFilesAtRightCommitSubscription: Subscription;

    private paramSub: Subscription;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private route: ActivatedRoute,
        private exerciseService: ExerciseService,
    ) {}

    ngOnDestroy(): void {
        this.templateRepoFilesSubscription?.unsubscribe();
        this.solutionRepoFilesSubscription?.unsubscribe();
        this.participationRepoFilesAtLeftCommitSubscription?.unsubscribe();
        this.participationRepoFilesAtRightCommitSubscription?.unsubscribe();
        this.paramSub?.unsubscribe();
        this.commitsInfoSubscription?.unsubscribe();
    }

    ngOnInit(): void {
        this.paramSub = this.route.params.subscribe((params) => {
            this.exerciseId = Number(params['exerciseId']);
            this.participationId = Number(params['participationId']);
            this.commitHash = params['commitHash'];
            this.exerciseService.getExerciseDetails(this.exerciseId).subscribe((res) => {
                this.exercise = res.body!;
                this.handleSubmissions();
                this.user = this.exercise.studentParticipations?.find((participation) => participation.id === this.participationId)?.student!;
                this.retrieveAndHandleCommits();
                if (this.previousSubmission && this.currentSubmission) {
                    this.programmingExerciseService.getDiffReportForSubmissions(this.exerciseId, this.previousSubmission.id!, this.currentSubmission.id!).subscribe((report) => {
                        this.handleNewReport(report!);
                    });
                } else if (this.currentSubmission) {
                    this.programmingExerciseService.getDiffReportForSubmissionWithTemplate(this.exerciseId, this.currentSubmission.id!).subscribe((report) => {
                        this.handleNewReport(report!);
                    });
                }
            });
        });
    }

    handleSubmissions() {
        const submissions = this.exercise.studentParticipations
            ?.find((participation) => participation.id === this.participationId)
            ?.submissions?.map((submission) => submission as ProgrammingSubmission)
            .sort((a, b) => (dayjs(b.submissionDate!).isAfter(dayjs(a.submissionDate!)) ? -1 : 1));
        if (submissions && submissions.length > 0) {
            for (let i = 0; i < submissions.length; i++) {
                if (submissions[i].commitHash === this.commitHash) {
                    this.currentSubmission = submissions[i];
                    if (i > 0) {
                        this.previousSubmission = submissions[i - 1];
                    }
                }
            }
        }
    }

    retrieveAndHandleCommits() {
        this.commitsInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitsInfoForParticipation(this.participationId).subscribe((commits) => {
            this.commits = commits.sort((a, b) => (dayjs(b.timestamp!).isAfter(dayjs(a.timestamp!)) ? 1 : -1));
            if (this.currentSubmission !== undefined) {
                this.currentCommit = this.commits.find((commit) => commit.hash === this.currentSubmission.commitHash)!;
            } else {
                // choose template commit
                this.currentCommit = this.commits[commits.length - 1];
            }
            if (this.previousSubmission !== undefined) {
                // choose template commit
                this.previousCommit = this.commits.find((commit) => commit.hash === this.previousSubmission.commitHash)!;
            } else {
                this.previousCommit = this.commits[commits.length - 1];
            }
        });
    }

    handleNewReport(report: ProgrammingExerciseGitDiffReport) {
        this.report = report;
        this.report.programmingExercise = this.exercise;
        this.report.leftCommitHash = this.previousCommit.hash;
        this.report.rightCommitHash = this.currentCommit.hash;
        this.report.participationIdForLeftCommit = this.participationId;
        this.report.participationIdForRightCommit = this.participationId;
        this.fetchParticipationRepoFiles();
    }

    private fetchParticipationRepoFiles() {
        this.participationRepoFilesAtLeftCommitSubscription = this.programmingExerciseParticipationService
            .getParticipationRepositoryFilesWithContentAtCommit(this.report.participationIdForLeftCommit!, this.report.leftCommitHash!)
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

    private fetchParticipationRepoFilesAtRightCommit() {
        this.participationRepoFilesAtRightCommitSubscription = this.programmingExerciseParticipationService
            .getParticipationRepositoryFilesWithContentAtCommit(this.report.participationIdForRightCommit!, this.report.rightCommitHash!)
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
