import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/entities/exercise.model';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming/programming-submission.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { tap } from 'rxjs/operators';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';

@Component({
    selector: 'jhi-commit-history',
    templateUrl: './commit-history.component.html',
})
export class CommitHistoryComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private programmingExerciseService = inject(ProgrammingExerciseService);

    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly dayjs = dayjs;

    participation: TemplateProgrammingExerciseParticipation | SolutionProgrammingExerciseParticipation | ProgrammingExerciseStudentParticipation;
    participationId: number;
    exerciseId: number;
    repositoryType: string;
    paramSub: Subscription;
    commits: CommitInfo[];
    commitsInfoSubscription: Subscription;
    participationSub: Subscription;

    exercise: ProgrammingExercise;

    isTestRepository = false;

    ngOnDestroy() {
        this.paramSub?.unsubscribe();
        this.commitsInfoSubscription?.unsubscribe();
        this.participationSub?.unsubscribe();
    }

    /**
     * On init, subscribe to the route params to get the participation id and load the participation.
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.participationId = Number(params['participationId']);
            this.exerciseId = Number(params['exerciseId']);
            this.repositoryType = params['repositoryType'];
            if (this.repositoryType) {
                this.loadDifferentParticipation();
            } else {
                this.loadStudentParticipation();
            }
        });
    }

    private loadDifferentParticipation() {
        this.participationSub = this.programmingExerciseService
            .findWithTemplateAndSolutionParticipation(this.exerciseId, true)
            .pipe(
                tap((exerciseRes) => {
                    this.exercise = exerciseRes.body!;
                    if (this.repositoryType === 'TEMPLATE') {
                        this.participation = this.exercise.templateParticipation!;
                        (this.participation as TemplateProgrammingExerciseParticipation).programmingExercise = this.exercise;
                        this.participation.results = this.participation.submissions
                            ?.filter((submission) => submission.results && submission.results?.length > 0)
                            .map((submission) => {
                                submission.results![0].participation = this.participation!;
                                return submission.results![0];
                            });
                    } else if (this.repositoryType === 'SOLUTION') {
                        this.participation = this.exercise.solutionParticipation!;
                        (this.participation as SolutionProgrammingExerciseParticipation).programmingExercise = this.exercise;
                        this.participation.results = this.participation.submissions
                            ?.filter((submission) => submission.results && submission.results?.length > 0)
                            .map((submission) => {
                                submission.results![0].participation = this.participation!;
                                return submission.results![0];
                            });
                    } else if (this.repositoryType === 'TESTS') {
                        this.isTestRepository = true;
                        this.participation = this.exercise.templateParticipation!;
                    }
                }),
            )
            .subscribe({
                next: () => {
                    this.handleCommits();
                },
            });
    }

    /**
     * Load the participation with all results. Calls the handleCommits method after the participation is loaded.
     * @private
     */
    private loadStudentParticipation() {
        this.participationSub = this.programmingExerciseParticipationService
            .getStudentParticipationWithAllResults(this.participationId)
            .pipe(
                tap((participation) => {
                    this.participation = participation;
                    this.participation.results?.forEach((result) => {
                        result.participation = participation!;
                    });
                }),
            )
            .subscribe({
                next: () => {
                    this.handleCommits();
                },
            });
    }

    /**
     * Retrieves the commit history for the participation and filters out the commits that have no submission.
     * The last commit is always the template commit and is added to the list of commits.
     * @private
     */
    private handleCommits() {
        if (this.repositoryType) {
            this.commitsInfoSubscription = this.programmingExerciseParticipationService
                .retrieveCommitHistoryForTemplateSolutionOrTests(this.exerciseId, this.repositoryType)
                .subscribe((commits) => {
                    this.commits = this.sortCommitsByTimestampDesc(commits);
                    if (!this.isTestRepository) {
                        this.setCommitResults();
                    }
                });
        } else {
            this.commitsInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitHistoryForParticipation(this.participation.id!).subscribe((commits) => {
                this.commits = this.sortCommitsByTimestampDesc(commits);
                this.setCommitResults();
            });
        }
    }

    /**
     * Sets the result of the commit if it exists.
     * @private
     */
    private setCommitResults() {
        this.commits.forEach((commit) => {
            this.participation.results?.forEach((result) => {
                const submission = result.submission as ProgrammingSubmission;
                if (submission) {
                    if (submission.commitHash === commit.hash) {
                        commit.result = result;
                    }
                }
            });
        });
    }

    /**
     * Sorts the commits by timestamp in descending order.
     * @param commitInfos the commits to sort
     * @private
     */
    private sortCommitsByTimestampDesc(commitInfos: CommitInfo[]) {
        return commitInfos.sort((a, b) => (dayjs(b.timestamp!).isAfter(dayjs(a.timestamp!)) ? 1 : -1));
    }
}
