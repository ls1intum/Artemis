import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { tap } from 'rxjs/operators';

@Component({
    selector: 'jhi-commit-history',
    templateUrl: './commit-history.component.html',
    styleUrl: './commit-history.component.scss',
})
export class CommitHistoryComponent implements OnInit, OnDestroy {
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly dayjs = dayjs;

    private exercise?: ProgrammingExercise;
    private participationUpdateListener: Subscription;
    studentParticipation: StudentParticipation;
    participationId: number;
    paramSub: Subscription;
    commits: CommitInfo[];
    commitsInfoSubscription: Subscription;

    constructor(
        private exerciseService: ExerciseService,
        private participationWebsocketService: ParticipationWebsocketService,
        private participationService: ParticipationService,
        private route: ActivatedRoute,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
    ) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.participationId = Number(params['participationId']);
            const exerciseId = Number(params['exerciseId']);
            this.loadExercise(exerciseId);
        });
    }

    ngOnDestroy() {
        this.participationUpdateListener?.unsubscribe();
        if (this.studentParticipation) {
            this.participationWebsocketService.unsubscribeForLatestResultOfParticipation(this.studentParticipation.id!, this.exercise!);
        }
        this.paramSub?.unsubscribe();
        this.commitsInfoSubscription?.unsubscribe();
    }

    loadExercise(exerciseId: number) {
        this.exerciseService.getExerciseDetails(exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            this.exercise = exerciseResponse.body!;
            this.handleParticipations();
        });
    }

    private handleParticipations() {
        this.participationService
            .findAllParticipationsByExercise(this.exercise!.id!)
            .pipe(
                tap((participationsResponse) => {
                    const participations = participationsResponse.body!;
                    this.studentParticipation = participations?.find((participation) => {
                        return participation.id === this.participationId;
                    })!;
                    this.studentParticipation.exercise = this.exercise;
                    this.studentParticipation = this.studentParticipation.exercise?.studentParticipations?.find((participation) => {
                        return participation.id === this.participationId;
                    })!;
                }),
            )
            .subscribe({
                next: () => {
                    this.mergeResultsAndSubmissionsForParticipations();
                    this.sortResults();
                    this.subscribeForNewResults();
                    this.handleCommits();
                },
            });
    }

    mergeResultsAndSubmissionsForParticipations() {
        this.studentParticipation = this.participationService
            .mergeStudentParticipations([this.studentParticipation])
            .find((participation) => participation.id === this.participationId)!;
        // Add exercise to studentParticipation, as the result component is dependent on its existence.
        this.studentParticipation.exercise = this.exercise;
    }

    sortResults() {
        this.studentParticipation.results = this.studentParticipation.results?.sort(this.resultSortFunction);
    }

    private resultSortFunction = (a: Result, b: Result) => {
        const aValue = dayjs(a.submission!.submissionDate).valueOf();
        const bValue = dayjs(b.submission!.submissionDate).valueOf();
        return aValue - bValue;
    };

    subscribeForNewResults() {
        if (this.exercise && this.studentParticipation) {
            this.participationWebsocketService.addParticipation(this.studentParticipation, this.exercise);
        }
        this.participationUpdateListener = this.participationWebsocketService.subscribeForParticipationChanges().subscribe((changedParticipation: StudentParticipation) => {
            if (changedParticipation && this.exercise && changedParticipation.exercise?.id === this.exercise.id) {
                if (this.studentParticipation.id === changedParticipation.id) {
                    this.studentParticipation = changedParticipation;
                }
            }
        });
    }

    handleCommits() {
        this.commitsInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitsInfoForParticipation(this.participationId).subscribe((commits) => {
            this.commits = [];
            for (let i = 0; i < commits.length - 1; i++) {
                const hasSubmission = this.studentParticipation.submissions?.some((submission) => {
                    const programmingSubmission = submission as ProgrammingSubmission;
                    return programmingSubmission.commitHash === commits[i].hash;
                });
                if (hasSubmission) {
                    this.commits.push(commits[i]);
                }
            }
            this.commits.push(commits[commits.length - 1]);
            this.commits = this.sortCommitsByTimestampDesc(this.commits);
            this.setCommitDetails();
        });
    }

    private setCommitDetails() {
        this.commits.forEach((commit) => {
            if (this.studentParticipation.student?.name! === commit.author) {
                commit.user = this.studentParticipation.student!;
            }
            this.studentParticipation.team?.students?.forEach((student) => {
                if (student.name! === commit.author) {
                    commit.user = student;
                }
            });
            this.studentParticipation.results?.forEach((result) => {
                const submission = result.submission as ProgrammingSubmission;
                if (submission) {
                    if (submission.commitHash === commit.hash) {
                        commit.result = result;
                    }
                }
            });
        });
    }

    private sortCommitsByTimestampDesc(commitInfos: CommitInfo[]) {
        return commitInfos.sort((a, b) => (dayjs(b.timestamp!).isAfter(dayjs(a.timestamp!)) ? 1 : -1));
    }
}
