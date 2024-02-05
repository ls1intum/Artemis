import { Component } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { Participation } from 'app/entities/participation/participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-commit-history',
    templateUrl: './commit-history.component.html',
    styleUrl: './commit-history.component.scss',
})
export class CommitHistoryComponent {
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly dayjs = dayjs;

    private exerciseId: number;
    private exercise?: Exercise;
    private participationUpdateListener: Subscription;
    private submissionSubscription: Subscription;
    sortedHistoryResults: Result[];
    studentParticipations: StudentParticipation[] = [];
    users: Map<string, User> = new Map<string, User>();
    participationId: number;
    paramSub: Subscription;

    constructor(
        private exerciseService: ExerciseService,
        private participationWebsocketService: ParticipationWebsocketService,
        private participationService: ParticipationService,
        private route: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.exerciseId = Number(params['exerciseId']);
            this.participationId = Number(params['participationId']);
            this.loadExercise();
        });
    }

    ngOnDestroy() {
        this.participationUpdateListener?.unsubscribe();
        if (this.studentParticipations) {
            this.studentParticipations.forEach((participation) => {
                this.participationWebsocketService.unsubscribeForLatestResultOfParticipation(participation.id!, this.exercise!);
            });
        }
        this.submissionSubscription?.unsubscribe();
        this.paramSub?.unsubscribe();
    }

    loadExercise() {
        this.studentParticipations = this.participationWebsocketService.getParticipationsForExercise(this.exerciseId);
        this.exerciseService.getExerciseDetails(this.exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            this.handleNewExercise(exerciseResponse.body!);
        });
    }

    handleNewExercise(newExercise: Exercise) {
        this.exercise = newExercise;
        this.filterUnfinishedResults(this.exercise.studentParticipations);
        this.mergeResultsAndSubmissionsForParticipations();
        this.subscribeForNewResults();
    }

    /**
     * Filters out any unfinished Results
     */
    private filterUnfinishedResults(participations?: StudentParticipation[]) {
        participations?.forEach((participation: Participation) => {
            if (participation.results) {
                participation.results = participation.results.filter((result: Result) => result.completionDate);
            }
        });
    }

    sortResults() {
        if (this.studentParticipations?.length) {
            this.studentParticipations.forEach((participation) => participation.results?.sort(this.resultSortFunction));
            this.sortedHistoryResults = this.studentParticipations
                .flatMap((participation) => participation.results ?? [])
                .sort((a, b) => (dayjs(b.completionDate).isAfter(dayjs(a.completionDate)) ? 1 : -1));
        }
    }

    private resultSortFunction = (a: Result, b: Result) => {
        const aValue = dayjs(a.completionDate!).valueOf();
        const bValue = dayjs(b.completionDate!).valueOf();
        return aValue - bValue;
    };

    mergeResultsAndSubmissionsForParticipations() {
        // if there are new student participation(s) from the server, we need to update this.studentParticipation
        if (this.exercise?.studentParticipations?.length) {
            this.studentParticipations = this.participationService.mergeStudentParticipations(this.exercise.studentParticipations);
            this.exercise.studentParticipations = this.studentParticipations;
            this.sortResults();
            // Add exercise to studentParticipation, as the result component is dependent on its existence.
            this.studentParticipations.forEach((participation) => (participation.exercise = this.exercise));
        } else if (this.studentParticipations?.length && this.exercise) {
            // otherwise we make sure that the student participation in exercise is correct
            this.exercise.studentParticipations = this.studentParticipations;
        }
    }

    subscribeForNewResults() {
        if (this.exercise && this.studentParticipations?.length) {
            this.studentParticipations.forEach((participation) => {
                this.participationWebsocketService.addParticipation(participation, this.exercise);
            });
        }
        this.participationUpdateListener = this.participationWebsocketService.subscribeForParticipationChanges().subscribe((changedParticipation: StudentParticipation) => {
            if (changedParticipation && this.exercise && changedParticipation.exercise?.id === this.exercise.id) {
                if (this.studentParticipations?.some((participation) => participation.id === changedParticipation.id)) {
                    this.exercise.studentParticipations = this.studentParticipations.map((participation) =>
                        participation.id === changedParticipation.id ? changedParticipation : participation,
                    );
                } else {
                    this.exercise.studentParticipations = [...this.studentParticipations, changedParticipation];
                }
                this.mergeResultsAndSubmissionsForParticipations();
            }
        });
    }
}
