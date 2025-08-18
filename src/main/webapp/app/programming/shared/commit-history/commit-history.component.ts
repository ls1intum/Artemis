import { Component, OnDestroy, OnInit, inject, input, linkedSignal } from '@angular/core';
import { Subscription } from 'rxjs';
import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CommitInfo, ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { tap } from 'rxjs/operators';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { CommitsInfoComponent } from '../commits-info/commits-info.component';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-commit-history',
    templateUrl: './commit-history.component.html',
    imports: [CommitsInfoComponent],
})
export class CommitHistoryComponent implements OnInit, OnDestroy {
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private programmingExerciseService = inject(ProgrammingExerciseService);

    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly dayjs = dayjs;

    participation: TemplateProgrammingExerciseParticipation | SolutionProgrammingExerciseParticipation | ProgrammingExerciseStudentParticipation;
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
    // acts as both participationId (USER repositories) and repositoryId (AUXILIARY repositories), undefined for TEMPLATE, SOLUTION and TEST
    repositoryId = input.required<number>();
    exerciseId = input.required<number>();
    repositoryType = input<RepositoryType>();
    internalRepositoryType = linkedSignal<RepositoryType>(() => (this.repositoryType() || RepositoryType.USER) as RepositoryType);

    /**
     * On init, subscribe to the route params to get the participation id and load the participation.
     */
    ngOnInit() {
        if (this.repositoryId() && this.internalRepositoryType() === RepositoryType.USER) {
            this.loadStudentParticipation();
        } else {
            this.loadDifferentParticipation();
        }
    }

    private loadDifferentParticipation() {
        this.participationSub = this.programmingExerciseService
            .findWithTemplateAndSolutionParticipation(this.exerciseId(), true)
            .pipe(
                tap((exerciseRes) => {
                    this.exercise = exerciseRes.body!;
                    if (this.internalRepositoryType() === 'TEMPLATE') {
                        this.participation = this.exercise.templateParticipation!;
                        (this.participation as TemplateProgrammingExerciseParticipation).programmingExercise = this.exercise;
                    } else if (this.internalRepositoryType() === 'SOLUTION') {
                        this.participation = this.exercise.solutionParticipation!;
                        (this.participation as SolutionProgrammingExerciseParticipation).programmingExercise = this.exercise;
                    } else if (this.internalRepositoryType() === 'TESTS') {
                        this.isTestRepository = true;
                        this.participation = this.exercise.templateParticipation!;
                    } else if (this.internalRepositoryType() === 'AUXILIARY') {
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
            .getStudentParticipationWithAllResults(this.repositoryId())
            .pipe(
                tap((participation) => {
                    this.participation = participation;
                    this.participation.submissions?.forEach((submission) => {
                        submission.results?.forEach((result) => {
                            result.submission = submission;
                        });
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
     * Retrieves the commit history and handles it depending on repository type
     * The last commit is always the template commit and is added to the list of commits.
     * @private
     */
    private handleCommits() {
        if (this.internalRepositoryType() === RepositoryType.USER) {
            this.handleParticipationCommits();
        } else if (this.internalRepositoryType() === RepositoryType.AUXILIARY) {
            this.handleAuxiliaryRepositoryCommits();
        } else {
            this.handleTemplateSolutionTestRepositoryCommits();
        }
    }

    /**
     * Retrieves the commit history and filters out the commits that have no submission.
     * The last commit is always the template commit and is added to the list of commits.
     * @private
     */
    private handleParticipationCommits() {
        this.commitsInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitHistoryForParticipation(this.participation.id!).subscribe((commits) => {
            this.commits = this.sortCommitsByTimestampDesc(commits);
            this.setCommitResults();
        });
    }

    /**
     * Retrieves the commit history for an auxiliary repository
     * The last commit is always the template commit and is added to the list of commits.
     * @private
     */
    private handleAuxiliaryRepositoryCommits() {
        this.commitsInfoSubscription = this.programmingExerciseParticipationService
            .retrieveCommitHistoryForAuxiliaryRepository(this.exerciseId(), this.repositoryId())
            .subscribe((commits) => {
                this.commits = this.sortCommitsByTimestampDesc(commits);
            });
    }

    /**
     * Retrieves the commit history for template/solution/test repositories.
     * The last commit is always the template commit and is added to the list of commits.
     * @private
     */
    private handleTemplateSolutionTestRepositoryCommits() {
        this.commitsInfoSubscription = this.programmingExerciseParticipationService
            .retrieveCommitHistoryForTemplateSolutionOrTests(this.exerciseId(), this.internalRepositoryType())
            .subscribe((commits) => {
                this.commits = this.sortCommitsByTimestampDesc(commits);
                if (!this.isTestRepository) {
                    this.setCommitResults();
                }
            });
    }

    /**
     * Sets the result of the commit if it exists.
     * @private
     */
    private setCommitResults() {
        const results = this.participation.submissions?.flatMap((submission) => submission.results ?? []) || [];
        this.commits.forEach((commit) => {
            commit.result = results.find((result) => {
                const submission = result.submission as ProgrammingSubmission;
                return submission && submission.commitHash === commit.hash;
            });
            if (commit.result?.submission) {
                commit.result.submission.participation = this.participation;
            }
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
