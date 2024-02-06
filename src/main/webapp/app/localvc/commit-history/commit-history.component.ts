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

@Component({
    selector: 'jhi-commit-history',
    templateUrl: './commit-history.component.html',
    styleUrl: './commit-history.component.scss',
})
export class CommitHistoryComponent implements OnInit, OnDestroy {
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly dayjs = dayjs;

    private exercise?: Exercise;
    private participationUpdateListener: Subscription;
    studentParticipation: StudentParticipation;
    users: Map<string, User> = new Map<string, User>();
    participationId: number;
    paramSub: Subscription;
    isLoading = false;

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
        this.exercise = newExercise;
        this.studentParticipation = this.exercise.studentParticipations!.find((participation) => participation.id === this.participationId)!;
        this.filterUnfinishedResults();
        this.mergeResultsAndSubmissionsForParticipations();
        this.sortResults();
        this.subscribeForNewResults();
        this.users.set(this.studentParticipation.student!.name!, this.studentParticipation.student!);
        if (this.studentParticipation.team) {
            this.studentParticipation.team.students!.forEach((student) => this.users.set(student.name!, student));
        }
    }

    /**
     * Filters out any unfinished Results
     */
    private filterUnfinishedResults() {
        this.studentParticipation.results = this.studentParticipation.results?.filter((result) => result.completionDate);
    }

    sortResults() {
        this.studentParticipation.results = this.studentParticipation.results?.sort(this.resultSortFunction);
    }

    private resultSortFunction = (a: Result, b: Result) => {
        const aValue = dayjs(a.completionDate!).valueOf();
        const bValue = dayjs(b.completionDate!).valueOf();
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
