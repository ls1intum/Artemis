import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { Exercise, ExerciseService, ExerciseType } from 'app/entities/exercise';
import { CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { Result } from 'app/entities/result';
import * as moment from 'moment';
import { AccountService } from 'app/core';

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
    public showMoreResults: boolean = false;

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
            });
        }
    }

    backToCourse() {
        this.$location.back();
    }

    get exerciseStatusBadge(): string {
        return moment(this.exercise.dueDate).isBefore(moment()) ? 'badge-danger' : 'badge-success';
    }

    exerciseRatedBadge(result: Result): string {
        return result.rated ? 'badge-success' : 'badge-info';
    }

    get exerciseIsOver(): boolean {
        return moment(this.exercise.dueDate).isBefore(moment());
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

    get currentResult(): Result {
        if (!this.exercise.participations) return null;
        let results = this.exercise.participations[0].results;
        return results.filter(el => el.rated).pop();
    }
}
