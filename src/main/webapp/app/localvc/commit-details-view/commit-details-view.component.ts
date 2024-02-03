import { Component } from '@angular/core';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { CachedRepositoryFilesService } from 'app/exercises/programming/manage/services/cached-repository-files.service';
import { Subscription } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ActivatedRoute } from '@angular/router';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming-submission.model';
import dayjs from 'dayjs';

@Component({
    selector: 'jhi-commit-details-view',
    templateUrl: './commit-details-view.component.html',
    styleUrl: './commit-details-view.component.scss',
})
export class CommitDetailsViewComponent {
    exercise: ProgrammingExercise;
    report: ProgrammingExerciseGitDiffReport;
    diffForTemplateAndSolution = false;
    cachedRepositoryFiles: Map<string, Map<string, string>> = new Map<string, Map<string, string>>();

    errorWhileFetchingRepos = false;
    leftCommitFileContentByPath: Map<string, string>;
    rightCommitFileContentByPath: Map<string, string>;
    isLoading = false;
    commitsInfoSubscription: Subscription;
    commits: CommitInfo[] = [];
    currentSubmission: ProgrammingSubmission;
    previousSubmission: ProgrammingSubmission;

    private templateRepoFilesSubscription: Subscription;
    private solutionRepoFilesSubscription: Subscription;
    private participationRepoFilesAtLeftCommitSubscription: Subscription;
    private participationRepoFilesAtRightCommitSubscription: Subscription;

    private paramSub: Subscription;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private cachedRepositoryFilesService: CachedRepositoryFilesService,
        private route: ActivatedRoute,
        private exerciseService: ExerciseService,
    ) {}

    ngOnInit(): void {
        this.paramSub = this.route.params.subscribe((params) => {
            const exerciseId = Number(params['exerciseId']);
            const participationId = Number(params['participationId']);
            const commitHash = params['commitHash'];
            this.exerciseService.getExerciseDetails(exerciseId).subscribe((res) => {
                this.exercise = res.body!;
                const submissions = this.exercise.studentParticipations
                    ?.find((participation) => participation.id === participationId)
                    ?.submissions?.map((submission) => submission as ProgrammingSubmission);
                if (submissions && submissions.length > 0) {
                    for (let i = 0; i < submissions.length; i++) {
                        if (submissions[i].commitHash === commitHash) {
                            this.currentSubmission = submissions[i];
                            if (i > 0) {
                                this.previousSubmission = submissions[i - 1];
                            } else {
                                this.previousSubmission = submissions[i];
                            }
                        }
                    }
                }
                console.error(this.previousSubmission);
                console.error(this.currentSubmission);
                this.programmingExerciseService.getDiffReportForSubmissions(exerciseId, this.previousSubmission.id!, this.currentSubmission.id!).subscribe((report) => {
                    this.report = report!;
                    this.report.programmingExercise = this.exercise;
                    this.commitsInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitsInfoForParticipation(participationId).subscribe((commits) => {
                        this.commits = this.sortCommitsByTimestampDesc(commits);
                        this.report.rightCommitHash = this.currentSubmission.commitHash;
                        this.report.participationIdForRightCommit = participationId;
                        if (this.commits.length > 1) {
                            this.report.leftCommitHash = this.previousSubmission.commitHash;
                            this.report.participationIdForLeftCommit = participationId;
                        } else {
                            this.report.leftCommitHash = this.report.templateRepositoryCommitHash;
                            this.report.participationIdForLeftCommit = participationId;
                        }
                        if (this.diffForTemplateAndSolution) {
                            this.loadFilesForTemplateAndSolution();
                        } else {
                            this.loadRepositoryFilesForParticipationsFromCacheIfAvailable();
                        }
                    });
                });
            });
        });
    }

    sortCommitsByTimestampDesc(commitInfos: CommitInfo[]) {
        return commitInfos.sort((a, b) => (dayjs(b.timestamp!).isAfter(dayjs(a.timestamp!)) ? 1 : -1));
    }

    ngOnDestroy(): void {
        this.templateRepoFilesSubscription?.unsubscribe();
        this.solutionRepoFilesSubscription?.unsubscribe();
        this.participationRepoFilesAtLeftCommitSubscription?.unsubscribe();
        this.participationRepoFilesAtRightCommitSubscription?.unsubscribe();
        this.paramSub?.unsubscribe();
        this.commitsInfoSubscription?.unsubscribe();
    }

    private loadFilesForTemplateAndSolution() {
        this.fetchTemplateRepoFiles();
        this.fetchSolutionRepoFiles();
    }

    private fetchSolutionRepoFiles() {
        this.solutionRepoFilesSubscription = this.programmingExerciseService.getSolutionRepositoryTestFilesWithContent(this.report.programmingExercise.id!).subscribe({
            next: (response: Map<string, string>) => {
                this.rightCommitFileContentByPath = response;
            },
            error: () => {
                this.errorWhileFetchingRepos = true;
            },
        });
    }

    private loadRepositoryFilesForParticipationsFromCacheIfAvailable() {
        if (this.report.participationIdForLeftCommit) {
            const key = this.report.leftCommitHash!;
            if (this.cachedRepositoryFiles.has(key)) {
                this.leftCommitFileContentByPath = this.cachedRepositoryFiles.get(key)!;
                this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable();
            } else {
                this.fetchParticipationRepoFilesAtLeftCommit();
            }
        } else {
            // if there is no left commit, we want to see the diff between the current submission and the template
            this.loadTemplateRepoFilesFromCacheIfAvailable();
            this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable();
        }
    }

    private fetchParticipationRepoFilesAtLeftCommit() {
        this.participationRepoFilesAtLeftCommitSubscription = this.programmingExerciseParticipationService
            .getParticipationRepositoryFilesWithContentAtCommit(this.report.participationIdForLeftCommit!, this.report.leftCommitHash!)
            .subscribe({
                next: (filesWithContent: Map<string, string>) => {
                    this.leftCommitFileContentByPath = filesWithContent;
                    this.cachedRepositoryFiles.set(this.report.leftCommitHash!, filesWithContent);
                    this.cachedRepositoryFilesService.emitCachedRepositoryFiles(this.cachedRepositoryFiles);
                    this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable();
                },
                error: () => {
                    this.errorWhileFetchingRepos = true;
                },
            });
    }

    private loadTemplateRepoFilesFromCacheIfAvailable() {
        const key = this.calculateTemplateMapKey();
        if (this.cachedRepositoryFiles.has(key)) {
            this.leftCommitFileContentByPath = this.cachedRepositoryFiles.get(key)!;
        } else {
            this.fetchTemplateRepoFiles();
        }
    }

    private loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable() {
        const key = this.report.rightCommitHash!;
        if (this.cachedRepositoryFiles.has(key)) {
            this.rightCommitFileContentByPath = this.cachedRepositoryFiles.get(key)!;
        } else {
            this.fetchParticipationRepoFilesAtRightCommit();
        }
    }

    private fetchTemplateRepoFiles() {
        const key = this.calculateTemplateMapKey();
        this.templateRepoFilesSubscription = this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(this.report.programmingExercise.id!).subscribe({
            next: (response: Map<string, string>) => {
                this.leftCommitFileContentByPath = response;
                this.cachedRepositoryFiles.set(key, response);
                this.cachedRepositoryFilesService.emitCachedRepositoryFiles(this.cachedRepositoryFiles);
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
                    this.cachedRepositoryFiles.set(this.report.rightCommitHash!, filesWithContent);
                    this.cachedRepositoryFilesService.emitCachedRepositoryFiles(this.cachedRepositoryFiles);
                },
                error: () => {
                    this.errorWhileFetchingRepos = true;
                },
            });
    }

    private calculateTemplateMapKey() {
        return this.report.programmingExercise.id! + '-template';
    }
}
