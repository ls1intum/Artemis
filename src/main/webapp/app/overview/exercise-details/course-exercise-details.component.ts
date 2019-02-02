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
    public showRatedResults: boolean = true;
    public showPracticeResults: boolean = true;


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

    get filteredResults(): Result[] {
        let results: Result[] = [];
        let participations = this.exercise.participations;
        if (!participations) return [];
        participations.forEach(participation => results = participation.results);
        results = results.filter(result => result.rated && this.showRatedResults || !result.rated && this.showPracticeResults);
        return results;
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
}
