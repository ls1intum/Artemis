import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { Exercise, ExerciseService, ExerciseType } from 'app/entities/exercise';
import { CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { Result } from 'app/entities/result';
import * as moment from 'moment';
import { AccountService } from 'app/core';
import { ExerciseIcon } from 'app/overview';

const MAX_RESULT_HISTORY_LENGTH = 5;

@Component({
    selector: 'jhi-course-exercise-details',
    templateUrl: './course-exercise-details.component.html',
    styleUrls: ['../course-overview.scss']
})
export class CourseExerciseDetailsComponent implements OnInit {
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
    public sortedResults: Result[];
    public sortedHistoryResult: Result[];

    constructor(private $location: Location, private exerciseService: ExerciseService,
                private courseService: CourseService,
                private accountService: AccountService,
                private courseCalculationService: CourseScoreCalculationService,
                private courseServer: CourseService,
                private route: ActivatedRoute) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.exerciseId = parseInt(params['exerciseId'], 10);
            this.courseId = parseInt(params['courseId'], 10);
        });

        if (this.exercise === undefined) {
            this.exerciseService.findResultsForExercise(this.exerciseId).subscribe((exercise: Exercise) => {
                this.exercise = exercise;
                this.exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.exercise.course);
                this.setExerciseStatusBadge();
                if (this.hasResults) {
                    this.sortedResults = this.exercise.participations[0].results.sort((a, b) => {
                        const aValue = moment(a.completionDate).valueOf();
                        const bValue = moment(b.completionDate).valueOf();
                        return aValue - bValue;
                    });
                    const sortedResultLength = this.sortedResults.length;
                    const startingElement = sortedResultLength - MAX_RESULT_HISTORY_LENGTH;
                    this.sortedHistoryResult = this.sortedResults.slice(startingElement, sortedResultLength);
                }
            });
        }
    }

    backToCourse() {
        this.$location.back();
    }

    setExerciseStatusBadge(): void {
        this.exerciseStatusBadge = moment(this.exercise.dueDate).isBefore(moment()) ? 'badge-danger' : 'badge-success';
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
        return results.filter(el => el.rated).pop();
    }

    get exerciseIcon(): ExerciseIcon {
        if (this.exercise.type === this.PROGRAMMING) {
            return {
                faIcon: 'keyboard',
                tooltip: 'This is a programming quiz'
            };
        } else if (this.exercise.type === ExerciseType.MODELING) {
            return {
                faIcon: 'project-diagram',
                tooltip: 'This is a modeling quiz'
            };
        } else if (this.exercise.type === ExerciseType.QUIZ) {
            return {
                faIcon: 'check-double',
                tooltip: 'This is a multiple choice quiz'
            };
        } else if (this.exercise.type === ExerciseType.TEXT) {
            return {
                faIcon: 'font',
                tooltip: 'This is a text quiz'
            };
        } else if (this.exercise.type === ExerciseType.FILE_UPLOAD) {
            return {
                faIcon: 'file-upload',
                tooltip: 'This is a file upload'
            };
        } else {
            return;
        }
    }
}
