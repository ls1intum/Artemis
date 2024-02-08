import { Component, OnDestroy, OnInit } from '@angular/core';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { Subscription } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ActivatedRoute } from '@angular/router';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming-submission.model';
import dayjs from 'dayjs';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';

@Component({
    selector: 'jhi-commit-details-view',
    templateUrl: './commit-details-view.component.html',
    styleUrl: './commit-details-view.component.scss',
})
export class CommitDetailsViewComponent implements OnDestroy, OnInit {
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
    studentParticipation: ProgrammingExerciseStudentParticipation;

    private templateRepoFilesSubscription: Subscription;
    private solutionRepoFilesSubscription: Subscription;
    private participationRepoFilesAtLeftCommitSubscription: Subscription;
    private participationRepoFilesAtRightCommitSubscription: Subscription;

    private paramSub: Subscription;
    private participationSub: Subscription;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private route: ActivatedRoute,
    ) {}

    ngOnDestroy(): void {
        this.templateRepoFilesSubscription?.unsubscribe();
        this.solutionRepoFilesSubscription?.unsubscribe();
        this.participationRepoFilesAtLeftCommitSubscription?.unsubscribe();
        this.participationRepoFilesAtRightCommitSubscription?.unsubscribe();
        this.paramSub?.unsubscribe();
        this.commitsInfoSubscription?.unsubscribe();
        this.participationSub?.unsubscribe();
    }

    ngOnInit(): void {
        this.paramSub = this.route.params.subscribe((params) => {
            this.exerciseId = Number(params['exerciseId']);
            this.participationId = Number(params['participationId']);
            this.commitHash = params['commitHash'];
            this.participationSub = this.programmingExerciseParticipationService.getStudentParticipationWithAllResults(this.participationId).subscribe((participation) => {
                this.studentParticipation = participation;
                this.handleSubmissions();
                this.retrieveAndHandleCommits();
                if (this.previousSubmission && this.currentSubmission) {
                    this.programmingExerciseService
                        .getGitDiffReportForCommitDetailsViewForSubmissions(this.exerciseId, this.previousSubmission.id!, this.currentSubmission.id!)
                        .subscribe((report) => {
                            this.handleNewReport(report!);
                        });
                } else if (this.currentSubmission) {
                    this.programmingExerciseService
                        .getGitDiffReportForCommitDetailsViewForSubmissionWithTemplate(this.exerciseId, this.currentSubmission.id!)
                        .subscribe((report) => {
                            this.handleNewReport(report!);
                        });
                }
            });
        });
    }

    private handleSubmissions() {
        const submissions = this.studentParticipation.submissions?.sort((a, b) => (dayjs(b.submissionDate!).isAfter(dayjs(a.submissionDate!)) ? -1 : 1)) as ProgrammingSubmission[];
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

    private retrieveAndHandleCommits() {
        this.commitsInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitHistoryForParticipation(this.participationId).subscribe((commits) => {
            this.commits = commits.sort((a, b) => (dayjs(b.timestamp!).isAfter(dayjs(a.timestamp!)) ? 1 : -1));
            if (this.currentSubmission !== undefined) {
                this.currentCommit = this.commits.find((commit) => commit.hash === this.currentSubmission.commitHash)!;
            } else {
                // choose template commit
                this.currentCommit = this.commits[commits.length - 1];
            }
            if (this.previousSubmission !== undefined) {
                this.previousCommit = this.commits.find((commit) => commit.hash === this.previousSubmission.commitHash)!;
            } else {
                // choose template commit
                this.previousCommit = this.commits[commits.length - 1];
            }
            this.findCommitUser();
        });
    }

    private findCommitUser() {
        if (this.studentParticipation.student?.name === this.currentCommit.author) {
            this.currentCommit.user = this.studentParticipation.student!;
        }
        this.studentParticipation.team?.students?.forEach((student) => {
            if (student.name === this.currentCommit.author) {
                this.currentCommit.user = student;
            }
        });
    }

    private handleNewReport(report: ProgrammingExerciseGitDiffReport) {
        this.report = report;
        this.report.programmingExercise = this.studentParticipation.exercise as ProgrammingExercise;
        this.report.leftCommitHash = this.previousCommit.hash;
        this.report.rightCommitHash = this.currentCommit.hash;
        this.report.participationIdForLeftCommit = this.participationId;
        this.report.participationIdForRightCommit = this.participationId;
        this.fetchParticipationRepoFiles();
    }

    private fetchParticipationRepoFiles() {
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
