import { Component, OnDestroy, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { HttpResponse } from '@angular/common/http';
import { Exercise, ExerciseCategory, ExerciseService, ExerciseType } from 'app/entities/exercise';
import { CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { Result } from 'app/entities/result';
import * as moment from 'moment';
import { AccountService, JhiWebsocketService, User } from 'app/core';
import { InitializationState, Participation, ParticipationService, ParticipationWebsocketService } from 'app/entities/participation';

const MAX_RESULT_HISTORY_LENGTH = 5;

@Component({
    selector: 'jhi-course-exercise-details',
    templateUrl: './course-exercise-details.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseExerciseDetailsComponent implements OnInit, OnDestroy {
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;
    readonly TEXT = ExerciseType.TEXT;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;
    private currentUser: User;
    private exerciseId: number;
    public courseId: number;
    private subscription: Subscription;
    public exercise: Exercise;
    public showMoreResults = false;
    public sortedResults: Result[] = [];
    public sortedHistoryResult: Result[];
    public exerciseCategories: ExerciseCategory[];
    private participationUpdateListener: Subscription;
    combinedParticipation: Participation;
    isAfterAssessmentDueDate: boolean;

    constructor(
        private $location: Location,
        private exerciseService: ExerciseService,
        private courseService: CourseService,
        private jhiWebsocketService: JhiWebsocketService,
        private accountService: AccountService,
        private courseCalculationService: CourseScoreCalculationService,
        private participationWebsocketService: ParticipationWebsocketService,
        private participationService: ParticipationService,
        private courseServer: CourseService,
        private route: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            const didExerciseChange = this.exerciseId !== parseInt(params['exerciseId'], 10);
            const didCourseChange = this.courseId !== parseInt(params['courseId'], 10);
            this.exerciseId = parseInt(params['exerciseId'], 10);
            this.courseId = parseInt(params['courseId'], 10);
            this.accountService.identity().then((user: User) => {
                this.currentUser = user;
            });
            if (didExerciseChange || didCourseChange) {
                this.loadExercise();
            }
        });
    }

    loadExercise() {
        this.exercise = null;
        this.exerciseService.findResultsForExercise(this.exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            this.exercise = exerciseResponse.body;
            this.exercise.participations = this.filterParticipations(this.exercise.participations);
            this.mergeResultsAndSubmissionsForParticipations();
            this.isAfterAssessmentDueDate = !this.exercise.assessmentDueDate || moment().isAfter(this.exercise.assessmentDueDate);
            this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.exercise);
            this.exercise.participations.forEach(participation => this.participationWebsocketService.addResultsToParticipation(participation, ...participation.results));
            this.subscribeForNewResults();
        });
    }

    /**
     * Filter for participations that belong to the current user only. Additionally, we make sure that all results that are not finished (i.e. completionDate is not set) are
     * removed from the participations. We also sort the participations so that FINISHED participations come first.
     */
    private filterParticipations(participations: Participation[]): Participation[] {
        if (!participations) {
            return null;
        }
        const filteredParticipations = participations.filter((participation: Participation) => participation.student && participation.student.id === this.currentUser.id);
        filteredParticipations.forEach((participation: Participation) => {
            if (participation.results) {
                participation.results = participation.results.filter((result: Result) => result.completionDate);
            }
        });
        this.sortParticipationsFinishedFirst(filteredParticipations);
        return filteredParticipations;
    }

    /**
     * Sort the given participations so that FINISHED participations come first.
     *
     * Note, that this function directly operates on the array passed as argument and does not return anything.
     */
    private sortParticipationsFinishedFirst(participations: Participation[]) {
        if (participations && participations.length > 1) {
            participations.sort((a, b) => (b.initializationState === InitializationState.FINISHED ? 1 : -1));
        }
    }

    ngOnDestroy() {
        if (this.participationUpdateListener) {
            this.participationUpdateListener.unsubscribe();
        }
    }

    sortResults() {
        if (this.hasResults) {
            this.sortedResults = this.combinedParticipation.results.sort((a, b) => {
                const aValue = moment(a.completionDate).valueOf();
                const bValue = moment(b.completionDate).valueOf();
                return aValue - bValue;
            });
            const sortedResultLength = this.sortedResults.length;
            const startingElement = Math.max(sortedResultLength - MAX_RESULT_HISTORY_LENGTH, 0);
            this.sortedHistoryResult = this.sortedResults.slice(startingElement, sortedResultLength);
        }
    }

    mergeResultsAndSubmissionsForParticipations() {
        if (this.exercise && this.exercise.participations && this.exercise.participations.length > 0) {
            this.combinedParticipation = this.participationService.mergeResultsAndSubmissionsForParticipations(this.exercise.participations);
            this.sortResults();
        }
    }

    subscribeForNewResults() {
        if (this.exercise && this.exercise.participations && this.exercise.participations.length > 0) {
            this.exercise.participations.forEach(participation => {
                this.participationWebsocketService.addParticipation(participation, this.exercise);
            });
        } else {
            this.participationWebsocketService.addExerciseForNewParticipation(this.exercise.id);
        }
        this.participationUpdateListener = this.participationWebsocketService.subscribeForParticipationChanges().subscribe((changedParticipation: Participation) => {
            if (changedParticipation && this.exercise && changedParticipation.exercise.id === this.exercise.id) {
                this.exercise.participations =
                    this.exercise.participations && this.exercise.participations.length > 0
                        ? this.exercise.participations.map(el => {
                              return el.id === changedParticipation.id ? changedParticipation : el;
                          })
                        : [changedParticipation];
                this.mergeResultsAndSubmissionsForParticipations();
            }
        });
    }

    backToCourse() {
        this.$location.back();
    }

    exerciseRatedBadge(result: Result): string {
        return result.rated ? 'badge-success' : 'badge-info';
    }

    get exerciseIsOver(): boolean {
        return moment(this.exercise.dueDate).isBefore(moment());
    }

    get hasMoreResults(): boolean {
        return this.sortedResults.length > MAX_RESULT_HISTORY_LENGTH;
    }

    get exerciseRouterLink(): string {
        if (this.exercise.type === ExerciseType.MODELING) {
            return `/course/${this.courseId}/exercise/${this.exercise.id}/assessment`;
        } else if (this.exercise.type === ExerciseType.TEXT) {
            return `/text/${this.exercise.id}/assessment`;
        } else {
            return;
        }
    }

    get showResults(): boolean {
        if (this.exercise.type === ExerciseType.MODELING || this.exercise.type === ExerciseType.TEXT) {
            return this.hasResults && this.isAfterAssessmentDueDate;
        }
        return this.hasResults;
    }

    get hasResults(): boolean {
        if (!this.exercise || !this.exercise.participations || this.exercise.participations.length === 0) {
            return false;
        }
        return this.exercise.participations.some((participation: Participation) => participation.results && participation.results.length > 0);
    }

    /**
     * Returns the latest finished result for modeling and text exercises. It does not have to be rated.
     * For other exercise types it returns a rated result.
     */
    get currentResult(): Result {
        if (!this.hasResults) {
            return null;
        }

        if (this.exercise.type === ExerciseType.MODELING || this.exercise.type === ExerciseType.TEXT) {
            return this.sortedResults.find((result: Result) => !!result.completionDate);
        }

        const ratedResults = this.sortedResults.filter((result: Result) => result.rated);
        const latestResult = ratedResults.length ? ratedResults[ratedResults.length - 1] : null;
        if (latestResult) {
            latestResult.participation = this.combinedParticipation;
        }
        return latestResult;
    }
}
