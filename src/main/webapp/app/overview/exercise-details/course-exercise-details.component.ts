import { Component, OnDestroy, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { HttpResponse } from '@angular/common/http';
import { Exercise, ExerciseCategory, ExerciseService, ExerciseType, getIcon } from 'app/entities/exercise';
import { CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { Result } from 'app/entities/result';
import * as moment from 'moment';
import { AccountService, JhiWebsocketService } from 'app/core';
import { Participation, ParticipationService, ParticipationWebsocketService } from 'app/entities/participation';

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

    formattedProblemStatement: string;

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
            if (didExerciseChange || didCourseChange) {
                this.loadExercise();
            }
        });
    }

    loadExercise() {
        this.exercise = null;
        const cachedParticipations = this.participationWebsocketService.getAllParticipationsForExercise(this.exerciseId);
        if (cachedParticipations && cachedParticipations.length > 0) {
            this.exerciseService.find(this.exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
                this.exercise = exerciseResponse.body;
                this.exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.exercise.course);
                this.exercise.participations = cachedParticipations;
                this.mergeResultsAndSubmissionsForParticipations();
                this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.exercise);
                this.subscribeForNewResults();
            });
        } else {
            this.exerciseService.findResultsForExercise(this.exerciseId).subscribe((exercise: Exercise) => {
                this.exercise = exercise;
                this.exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.exercise.course);
                this.mergeResultsAndSubmissionsForParticipations();
                this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.exercise);
                this.subscribeForNewResults();
            });
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
            if (changedParticipation) {
                this.exercise.participations = this.exercise.participations
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

    get hasResults(): boolean {
        const hasParticipations = this.exercise.participations && this.exercise.participations[0];
        return hasParticipations && this.exercise.participations[0].results && this.exercise.participations[0].results.length > 0;
    }

    get currentResult(): Result {
        if (!this.exercise.participations || !this.exercise.participations[0].results) {
            return null;
        }
        const results = this.exercise.participations[0].results;
        const currentResult = results.filter(el => el.rated).pop();
        if (currentResult) {
            currentResult.participation = this.exercise.participations[0];
        }
        return currentResult;
    }
}
