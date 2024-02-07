import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

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
    users: Map<string, User> = new Map<string, User>();
    participationId: number;
    paramSub: Subscription;
    resultsMap: Map<string, Result> = new Map<string, Result>();

    constructor(
        private exerciseService: ExerciseService,
        private participationWebsocketService: ParticipationWebsocketService,
        private participationService: ParticipationService,
        private route: ActivatedRoute,
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
    }

    loadExercise(exerciseId: number) {
        this.exerciseService.getExerciseDetails(exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            this.handleNewExercise(exerciseResponse.body!);
        });
    }

    handleNewExercise(newExercise: Exercise) {
        this.exercise = newExercise as ProgrammingExercise;
        this.handleParticipations();
        this.studentParticipation = this.exercise!.studentParticipations!.find((participation) => participation.id === this.participationId)!;
        this.mergeResultsAndSubmissionsForParticipations();
        this.sortResults();
        this.subscribeForNewResults();
        this.users.set(this.studentParticipation.student!.name!, this.studentParticipation.student!);
        if (this.studentParticipation.team) {
            this.studentParticipation.team.students!.forEach((student) => this.users.set(student.name!, student));
        }
        this.studentParticipation.results?.forEach((result) => {
            const submission = result.submission as ProgrammingSubmission;
            if (submission) {
                this.resultsMap.set(submission.commitHash!, result);
            }
        });
    }

    handleParticipations() {
        this.participationService.findAllParticipationsByExercise(this.exercise!.id!).subscribe((participations) => {
            this.exercise!.studentParticipations = participations.body!;
        });
    }

    sortResults() {
        this.studentParticipation.results = this.studentParticipation.results?.sort(this.resultSortFunction);
    }

    private resultSortFunction = (a: Result, b: Result) => {
        const aValue = dayjs(a.submission!.submissionDate).valueOf();
        const bValue = dayjs(b.submission!.submissionDate).valueOf();
        return aValue - bValue;
    };

    mergeResultsAndSubmissionsForParticipations() {
        this.studentParticipation = this.participationService
            .mergeStudentParticipations([this.studentParticipation])
            .find((participation) => participation.id === this.participationId)!;
        // Add exercise to studentParticipation, as the result component is dependent on its existence.
        this.studentParticipation.exercise = this.exercise;
    }

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
}
