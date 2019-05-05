import { Component, OnInit, OnDestroy } from '@angular/core';
import { Location } from '@angular/common';
import { Exercise, ExerciseCategory, ExerciseService, ExerciseType, getIcon } from 'app/entities/exercise';
import { CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { Result, ResultWebsocketService } from 'app/entities/result';
import * as moment from 'moment';
import { AccountService, JhiWebsocketService } from 'app/core';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { distinctUntilChanged } from 'rxjs/operators';

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
    public exerciseStatusBadge = 'badge-success';
    public sortedResults: Result[] = [];
    public sortedHistoryResult: Result[];
    public exerciseCategories: ExerciseCategory[];
    private resultSubscription: Subscription;

    formattedProblemStatement: string | null;

    getIcon = getIcon;

    constructor(
        private $location: Location,
        private exerciseService: ExerciseService,
        private courseService: CourseService,
        private jhiWebsocketService: JhiWebsocketService,
        private accountService: AccountService,
        private courseCalculationService: CourseScoreCalculationService,
        private courseServer: CourseService,
        private route: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdown,
        private resultWebsocketService: ResultWebsocketService,
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
        this.exerciseService.findResultsForExercise(this.exerciseId).subscribe((exercise: Exercise) => {
            this.exercise = exercise;
            this.exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.exercise.course!);
            this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.course!);
            this.formattedProblemStatement = this.artemisMarkdown.htmlForMarkdown(this.exercise.problemStatement!);
            if (this.hasResults) {
                this.sortedResults = this.exercise.participations[0].results.sort((a, b) => {
                    const aValue = moment(a.completionDate!).valueOf();
                    const bValue = moment(b.completionDate!).valueOf();
                    return aValue - bValue;
                });
                const sortedResultLength = this.sortedResults.length;
                const startingElement = sortedResultLength - MAX_RESULT_HISTORY_LENGTH;
                this.sortedHistoryResult = this.sortedResults.slice(startingElement, sortedResultLength);
            }
            this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.exercise);
            this.subscribeForNewResults(this.exercise);
        });
    }

    ngOnDestroy() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
    }

    subscribeForNewResults(exercise: Exercise) {
        this.accountService.identity().then(user => {
            if (this.exercise && this.exercise.participations && this.exercise.participations.length > 0) {
                const participation = this.exercise.participations[0];
                if (participation) {
                    this.setupResultWebsocket(participation.id);
                }
            }
        });
    }

    private setupResultWebsocket(participationId: number) {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
        this.resultWebsocketService.subscribeResultForParticipation(participationId).then(observable => {
            this.resultSubscription = observable
                .pipe(distinctUntilChanged(({ id: id1 }: Result, { id: id2 }: Result) => id1 === id2))
                .subscribe((result: Result) => this.handleNewResult(result));
        });
    }

    handleNewResult(result: Result) {
        const participation = this.exercise.participations[0];
        if (participation) {
            const results = participation.results;
            if (!results.some(el => el.id === result.id)) {
                participation.results.push(result);
            }
        }
    }

    backToCourse() {
        this.$location.back();
    }

    exerciseRatedBadge(result: Result): string {
        return result.rated ? 'badge-success' : 'badge-info';
    }

    get exerciseIsOver(): boolean {
        return moment(this.exercise.dueDate!).isBefore(moment());
    }

    get hasMoreResults(): boolean {
        return this.sortedResults.length > MAX_RESULT_HISTORY_LENGTH;
    }

    get exerciseRouterLink(): string | null {
        if (this.exercise.type === ExerciseType.MODELING) {
            return `/course/${this.courseId}/exercise/${this.exercise.id}/assessment`;
        } else if (this.exercise.type === ExerciseType.TEXT) {
            return `/text/${this.exercise.id}/assessment`;
        } else {
            return null;
        }
    }

    get hasResults(): boolean {
        const hasParticipations = this.exercise.participations && this.exercise.participations[0];
        return hasParticipations && this.exercise.participations[0].results && this.exercise.participations[0].results.length > 0;
    }

    get currentResult(): Result | null {
        if (!this.exercise.participations || !this.exercise.participations[0].results) {
            return null;
        }
        const results = this.exercise.participations[0].results;
        return results.filter(el => el.rated).pop()!;
    }
}
